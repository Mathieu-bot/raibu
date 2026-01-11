# Migration Poja Lambda → Hybrid Architecture (ECS + Lambda)

## 🎯 Goal
Raibu requires persistent WebSocket connections for real-time video chat. AWS Lambda is not suitable for this use case. We need to migrate to a hybrid architecture:

- **ECS Fargate**: For the WebSocket/WebRTC server (real time)
- **Lambda** (existing): For the REST API (stateless endpoints)

---

## 📊 Target architecture

```
┌─────────────────┐
│   CloudFront    │  CDN for React frontend
└────────┬────────┘
         │
    ┌────▼─────┐
    │   ALB    │  Application Load Balancer
    └────┬─────┘
         │
    ┌────▼────────────────────────┐
    │                             │
┌───▼────┐                 ┌──────▼───────┐
│  ECS   │ WebSocket       │   Lambda     │ REST API
│ Fargate│ /ws             │   (Poja)     │ /api/*
└───┬────┘                 └──────┬───────┘
    │                             │
    └──────────┬──────────────────┘
               │
        ┌──────▼──────┐
        │ RDS Postgres│
        └─────────────┘
```

---

## 🔧 Migration steps

### 1. Create the ECS infrastructure with Terraform

Create `infrastructure/terraform/ecs.tf`:

```hcl
# VPC and subnets
resource "aws_vpc" "raibu_vpc" {
  cidr_block = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support = true
  
  tags = {
    Name = "raibu-vpc"
  }
}

resource "aws_subnet" "public_subnet_1" {
  vpc_id = aws_vpc.raibu_vpc.id
  cidr_block = "10.0.1.0/24"
  availability_zone = "eu-west-3a"
  map_public_ip_on_launch = true
}

resource "aws_subnet" "public_subnet_2" {
  vpc_id = aws_vpc.raibu_vpc.id
  cidr_block = "10.0.2.0/24"
  availability_zone = "eu-west-3b"
  map_public_ip_on_launch = true
}

# Security Group
resource "aws_security_group" "ecs_sg" {
  name = "raibu-ecs-sg"
  vpc_id = aws_vpc.raibu_vpc.id
  
  ingress {
    from_port = 8080
    to_port = 8080
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ECS Cluster
resource "aws_ecs_cluster" "raibu_cluster" {
  name = "raibu-cluster"
}

# ECR Repository
resource "aws_ecr_repository" "raibu_backend" {
  name = "raibu-backend"
}

# ECS Task Definition
resource "aws_ecs_task_definition" "raibu_task" {
  family = "raibu-backend"
  network_mode = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu = "512"
  memory = "1024"
  execution_role_arn = aws_iam_role.ecs_execution_role.arn
  
  container_definitions = jsonencode([
    {
      name = "raibu-backend"
      image = "${aws_ecr_repository.raibu_backend.repository_url}:latest"
      portMappings = [
        {
          containerPort = 8080
          protocol = "tcp"
        }
      ]
      environment = [
        {
          name = "DATABASE_URL"
          value = "jdbc:postgresql://${aws_db_instance.raibu_db.endpoint}/raibu"
        },
        {
          name = "DATABASE_USERNAME"
          value = "raibu_admin"
        }
      ]
      secrets = [
        {
          name = "DATABASE_PASSWORD"
          valueFrom = aws_secretsmanager_secret.db_password.arn
        }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group" = "/ecs/raibu"
          "awslogs-region" = "eu-west-3"
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])
}

# ECS Service
resource "aws_ecs_service" "raibu_service" {
  name = "raibu-service"
  cluster = aws_ecs_cluster.raibu_cluster.id
  task_definition = aws_ecs_task_definition.raibu_task.arn
  desired_count = 2
  launch_type = "FARGATE"
  
  network_configuration {
    subnets = [
      aws_subnet.public_subnet_1.id,
      aws_subnet.public_subnet_2.id
    ]
    security_groups = [aws_security_group.ecs_sg.id]
    assign_public_ip = true
  }
  
  load_balancer {
    target_group_arn = aws_lb_target_group.raibu_tg.arn
    container_name = "raibu-backend"
    container_port = 8080
  }
}

# Application Load Balancer
resource "aws_lb" "raibu_alb" {
  name = "raibu-alb"
  internal = false
  load_balancer_type = "application"
  security_groups = [aws_security_group.ecs_sg.id]
  subnets = [
    aws_subnet.public_subnet_1.id,
    aws_subnet.public_subnet_2.id
  ]
}

resource "aws_lb_target_group" "raibu_tg" {
  name = "raibu-tg"
  port = 8080
  protocol = "HTTP"
  vpc_id = aws_vpc.raibu_vpc.id
  target_type = "ip"
  
  health_check {
    path = "/ping"
    healthy_threshold = 2
    unhealthy_threshold = 3
    timeout = 5
    interval = 30
  }
  
  # Support WebSocket
  stickiness {
    type = "lb_cookie"
    enabled = true
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.raibu_alb.arn
  port = "80"
  protocol = "HTTP"
  
  default_action {
    type = "forward"
    target_group_arn = aws_lb_target_group.raibu_tg.arn
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "raibu_db" {
  identifier = "raibu-db"
  engine = "postgres"
  engine_version = "15.4"
  instance_class = "db.t3.micro"
  allocated_storage = 20
  storage_type = "gp2"
  
  db_name = "raibu"
  username = "raibu_admin"
  password = random_password.db_password.result
  
  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  db_subnet_group_name = aws_db_subnet_group.raibu_db_subnet.name
  
  skip_final_snapshot = true
  publicly_accessible = false
}

resource "aws_db_subnet_group" "raibu_db_subnet" {
  name = "raibu-db-subnet"
  subnet_ids = [
    aws_subnet.public_subnet_1.id,
    aws_subnet.public_subnet_2.id
  ]
}

resource "aws_security_group" "rds_sg" {
  name = "raibu-rds-sg"
  vpc_id = aws_vpc.raibu_vpc.id
  
  ingress {
    from_port = 5432
    to_port = 5432
    protocol = "tcp"
    security_groups = [aws_security_group.ecs_sg.id]
  }
}

# Secrets Manager for the password
resource "random_password" "db_password" {
  length = 32
  special = true
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "raibu/db-password"
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# IAM Roles
resource "aws_iam_role" "ecs_execution_role" {
  name = "raibu-ecs-execution-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Effect = "Allow"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_policy" {
  role = aws_iam_role.ecs_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Outputs
output "alb_dns_name" {
  value = aws_lb.raibu_alb.dns_name
  description = "Load Balancer DNS"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.raibu_backend.repository_url
}

output "db_endpoint" {
  value = aws_db_instance.raibu_db.endpoint
  sensitive = true
}
```

### 2. Create the Dockerfile

Create `Dockerfile` at the project root:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3. Deployment scripts

Create `infrastructure/scripts/deploy-ecs.sh`:

```bash
#!/bin/bash
set -e

AWS_REGION="eu-west-3"
ECR_REPO=$(terraform output -raw ecr_repository_url)

# Build et push de l'image Docker
echo "🐳 Building Docker image..."
docker build -t raibu-backend .

echo "🔐 Login to ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REPO

echo "📤 Pushing to ECR..."
docker tag raibu-backend:latest $ECR_REPO:latest
docker push $ECR_REPO:latest

echo "🔄 Updating ECS service..."
aws ecs update-service \
  --cluster raibu-cluster \
  --service raibu-service \
  --force-new-deployment \
  --region $AWS_REGION

echo "✅ Deployment completed!"
```

### 4. Deploy the infrastructure

```bash
cd infrastructure/terraform
terraform init
terraform plan
terraform apply

# Deploy the application
cd ../..
chmod +x infrastructure/scripts/deploy-ecs.sh
./infrastructure/scripts/deploy-ecs.sh
```

---

## 🔀 Lambda + ECS coexistence

Keep Lambda for stateless endpoints via Poja:
- `/ping`
- `/health`
- Other REST endpoints if needed

ECS handles:
- `/ws` → WebSocket for WebRTC signaling
- `/users`, `/reports` → REST API with JPA

Configure the ALB to route based on the path:
```hcl
resource "aws_lb_listener_rule" "websocket" {
  listener_arn = aws_lb_listener.http.arn
  
  action {
    type = "forward"
    target_group_arn = aws_lb_target_group.raibu_tg.arn
  }
  
  condition {
    path_pattern {
      values = ["/ws", "/users/*", "/reports/*"]
    }
  }
}
```

---

## 💰 Estimated costs (eu-west-3)

- **ECS Fargate**: 2 tasks × 0.5 vCPU × 1GB = ~$30/month
- **RDS db.t3.micro**: ~$15/month
- **ALB**: ~$20/month
- **Total**: ~$65/month + traffic

---

## 🚀 Next steps

1. ✅ Spring Boot backend with WebSocket (done)
2. 🔧 Create the Terraform infrastructure
3. 📦 Build and deploy on ECS
4. 🎨 Create the React frontend with WebRTC
5. 🔐 Add authentication (optional)
6. 📊 CloudWatch monitoring

---

## 📚 Resources

- [AWS ECS Fargate docs](https://docs.aws.amazon.com/ecs/latest/developerguide/AWS_Fargate.html)
- [WebRTC documentation](https://webrtc.org/)
- [Spring WebSocket docs](https://docs.spring.io/spring-framework/reference/web/websocket.html)
