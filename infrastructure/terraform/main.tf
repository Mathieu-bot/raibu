# ============================================
# Raibu Infrastructure - ECS Fargate Setup
# ============================================
# This Terraform configuration creates the complete infrastructure
# needed to run Raibu on AWS ECS Fargate with WebSocket support
# ============================================

# ============================================
# Provider Configuration
# ============================================
terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }

  # Uncomment to use S3 backend for state management in production
  # backend "s3" {
  #   bucket = "raibu-terraform-state"
  #   key    = "raibu/terraform.tfstate"
  #   region = "eu-west-3"
  # }
}

# Configure AWS provider for eu-west-3 (Paris region)
provider "aws" {
  region = var.aws_region
}

# ============================================
# VPC and Networking
# ============================================
# Creates a Virtual Private Cloud to isolate Raibu resources

resource "aws_vpc" "raibu_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true  # Required for RDS and ECS to resolve DNS names
  enable_dns_support   = true
  
  tags = {
    Name        = "${var.project_name}-vpc"
    Environment = var.environment
    Project     = var.project_name
  }
}

# Internet Gateway - allows resources in public subnets to access the internet
resource "aws_internet_gateway" "raibu_igw" {
  vpc_id = aws_vpc.raibu_vpc.id
  
  tags = {
    Name        = "${var.project_name}-igw"
    Environment = var.environment
  }
}

# Public Subnet 1 (Availability Zone A)
# ECS tasks and Load Balancer will be deployed here
resource "aws_subnet" "public_subnet_1" {
  vpc_id                  = aws_vpc.raibu_vpc.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true  # Automatically assign public IPs to resources
  
  tags = {
    Name        = "${var.project_name}-public-subnet-1"
    Environment = var.environment
    Tier        = "Public"
  }
}

# Public Subnet 2 (Availability Zone B)
# Required for high availability - ALB needs at least 2 AZs
resource "aws_subnet" "public_subnet_2" {
  vpc_id                  = aws_vpc.raibu_vpc.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${var.aws_region}b"
  map_public_ip_on_launch = true
  
  tags = {
    Name        = "${var.project_name}-public-subnet-2"
    Environment = var.environment
    Tier        = "Public"
  }
}

# Private Subnet 1 (Availability Zone A)
# Database will be deployed here for security
resource "aws_subnet" "private_subnet_1" {
  vpc_id            = aws_vpc.raibu_vpc.id
  cidr_block        = "10.0.11.0/24"
  availability_zone = "${var.aws_region}a"
  
  tags = {
    Name        = "${var.project_name}-private-subnet-1"
    Environment = var.environment
    Tier        = "Private"
  }
}

# Private Subnet 2 (Availability Zone B)
# RDS requires at least 2 subnets in different AZs
resource "aws_subnet" "private_subnet_2" {
  vpc_id            = aws_vpc.raibu_vpc.id
  cidr_block        = "10.0.12.0/24"
  availability_zone = "${var.aws_region}b"
  
  tags = {
    Name        = "${var.project_name}-private-subnet-2"
    Environment = var.environment
    Tier        = "Private"
  }
}

# Route Table for Public Subnets
# Directs internet-bound traffic through the Internet Gateway
resource "aws_route_table" "public_rt" {
  vpc_id = aws_vpc.raibu_vpc.id
  
  route {
    cidr_block = "0.0.0.0/0"  # All internet traffic
    gateway_id = aws_internet_gateway.raibu_igw.id
  }
  
  tags = {
    Name        = "${var.project_name}-public-rt"
    Environment = var.environment
  }
}

# Associate public subnets with the public route table
resource "aws_route_table_association" "public_subnet_1_association" {
  subnet_id      = aws_subnet.public_subnet_1.id
  route_table_id = aws_route_table.public_rt.id
}

resource "aws_route_table_association" "public_subnet_2_association" {
  subnet_id      = aws_subnet.public_subnet_2.id
  route_table_id = aws_route_table.public_rt.id
}

# ============================================
# Security Groups
# ============================================

# Security Group for Application Load Balancer
# Controls inbound traffic to the ALB from the internet
resource "aws_security_group" "alb_sg" {
  name        = "${var.project_name}-alb-sg"
  description = "Security group for Application Load Balancer - allows HTTP/HTTPS traffic"
  vpc_id      = aws_vpc.raibu_vpc.id
  
  # Allow HTTP traffic from anywhere
  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # Allow HTTPS traffic from anywhere (for production with SSL certificate)
  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # Allow all outbound traffic
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"  # -1 means all protocols
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name        = "${var.project_name}-alb-sg"
    Environment = var.environment
  }
}

# Security Group for ECS Tasks
# Controls traffic between ALB and ECS containers
resource "aws_security_group" "ecs_sg" {
  name        = "${var.project_name}-ecs-sg"
  description = "Security group for ECS tasks - allows traffic from ALB on port 8080"
  vpc_id      = aws_vpc.raibu_vpc.id
  
  # Allow traffic from ALB on application port 8080
  ingress {
    description     = "Traffic from ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb_sg.id]
  }
  
  # Allow all outbound traffic (needed for database access, API calls, etc.)
  egress {
    description = "All outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name        = "${var.project_name}-ecs-sg"
    Environment = var.environment
  }
}

# Security Group for RDS PostgreSQL Database
# Only allows traffic from ECS tasks
resource "aws_security_group" "rds_sg" {
  name        = "${var.project_name}-rds-sg"
  description = "Security group for RDS PostgreSQL - allows traffic from ECS only"
  vpc_id      = aws_vpc.raibu_vpc.id
  
  # Allow PostgreSQL traffic only from ECS tasks
  ingress {
    description     = "PostgreSQL from ECS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_sg.id]
  }
  
  # No outbound rules needed for RDS (it doesn't initiate connections)
  
  tags = {
    Name        = "${var.project_name}-rds-sg"
    Environment = var.environment
  }
}

# ============================================
# RDS PostgreSQL Database
# ============================================

# Database Subnet Group - defines where RDS can be deployed
resource "aws_db_subnet_group" "raibu_db_subnet" {
  name        = "${var.project_name}-db-subnet"
  description = "Subnet group for RDS PostgreSQL database"
  subnet_ids  = [
    aws_subnet.private_subnet_1.id,
    aws_subnet.private_subnet_2.id
  ]
  
  tags = {
    Name        = "${var.project_name}-db-subnet"
    Environment = var.environment
  }
}

# Generate a random password for the database
resource "random_password" "db_password" {
  length  = 32
  special = true
  # Exclude characters that might cause issues in connection strings
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Store the database password securely in AWS Secrets Manager
resource "aws_secretsmanager_secret" "db_password" {
  name        = "${var.project_name}/db-password-${var.environment}"
  description = "PostgreSQL database password for Raibu ${var.environment}"
  
  tags = {
    Name        = "${var.project_name}-db-password"
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db_password.result
}

# RDS PostgreSQL Instance
resource "aws_db_instance" "raibu_db" {
  identifier     = "${var.project_name}-db-${var.environment}"
  engine         = "postgres"
  engine_version = "15.4"
  
  # Instance size - can be scaled up in production
  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"  # General Purpose SSD (faster than gp2)
  storage_encrypted = true   # Encrypt data at rest for security
  
  # Database credentials
  db_name  = "raibu"
  username = var.db_username
  password = random_password.db_password.result
  
  # Network configuration
  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  db_subnet_group_name   = aws_db_subnet_group.raibu_db_subnet.name
  publicly_accessible    = false  # Database should not be accessible from internet
  
  # Backup configuration
  backup_retention_period = var.environment == "prod" ? 7 : 1  # Keep backups for 7 days in production
  backup_window          = "03:00-04:00"  # UTC time for automated backups
  maintenance_window     = "sun:04:00-sun:05:00"  # UTC time for maintenance
  
  # High availability (only for production)
  multi_az = var.environment == "prod" ? true : false
  
  # Delete protection for production
  deletion_protection = var.environment == "prod" ? true : false
  skip_final_snapshot = var.environment != "prod"  # Create snapshot before deletion in prod
  
  # Performance Insights for monitoring (optional, additional cost)
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
  
  tags = {
    Name        = "${var.project_name}-db"
    Environment = var.environment
  }
}

# ============================================
# ECR Repository for Docker Images
# ============================================

resource "aws_ecr_repository" "raibu_backend" {
  name                 = "${var.project_name}-backend"
  image_tag_mutability = "MUTABLE"  # Allow overwriting tags like "latest"
  
  # Scan images for vulnerabilities on push
  image_scanning_configuration {
    scan_on_push = true
  }
  
  # Encryption at rest
  encryption_configuration {
    encryption_type = "AES256"
  }
  
  tags = {
    Name        = "${var.project_name}-backend"
    Environment = var.environment
  }
}

# Lifecycle policy to clean up old images and save storage costs
resource "aws_ecr_lifecycle_policy" "raibu_backend_policy" {
  repository = aws_ecr_repository.raibu_backend.name
  
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 10 images"
        selection = {
          tagStatus     = "any"
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ============================================
# ECS Cluster
# ============================================

resource "aws_ecs_cluster" "raibu_cluster" {
  name = "${var.project_name}-cluster-${var.environment}"
  
  # Enable Container Insights for detailed monitoring
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  
  tags = {
    Name        = "${var.project_name}-cluster"
    Environment = var.environment
  }
}

# CloudWatch Log Group for ECS task logs
resource "aws_cloudwatch_log_group" "ecs_logs" {
  name              = "/ecs/${var.project_name}-${var.environment}"
  retention_in_days = var.environment == "prod" ? 30 : 7  # Keep logs longer in production
  
  tags = {
    Name        = "${var.project_name}-ecs-logs"
    Environment = var.environment
  }
}

# ============================================
# IAM Roles and Policies
# ============================================

# IAM Role for ECS Task Execution
# This role is used by ECS to pull images and write logs
resource "aws_iam_role" "ecs_execution_role" {
  name = "${var.project_name}-ecs-execution-role-${var.environment}"
  
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
  
  tags = {
    Name        = "${var.project_name}-ecs-execution-role"
    Environment = var.environment
  }
}

# Attach AWS managed policy for ECS task execution
resource "aws_iam_role_policy_attachment" "ecs_execution_policy" {
  role       = aws_iam_role.ecs_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Additional policy to allow reading secrets from Secrets Manager
resource "aws_iam_role_policy" "ecs_secrets_policy" {
  name = "${var.project_name}-ecs-secrets-policy"
  role = aws_iam_role.ecs_execution_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          aws_secretsmanager_secret.db_password.arn
        ]
      }
    ]
  })
}

# IAM Role for ECS Task (used by the application at runtime)
resource "aws_iam_role" "ecs_task_role" {
  name = "${var.project_name}-ecs-task-role-${var.environment}"
  
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
  
  tags = {
    Name        = "${var.project_name}-ecs-task-role"
    Environment = var.environment
  }
}

# ============================================
# ECS Task Definition
# ============================================

resource "aws_ecs_task_definition" "raibu_task" {
  family                   = "${var.project_name}-backend"
  network_mode             = "awsvpc"  # Required for Fargate
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_task_cpu     # 512 = 0.5 vCPU
  memory                   = var.ecs_task_memory  # 1024 = 1 GB
  
  # IAM roles
  execution_role_arn = aws_iam_role.ecs_execution_role.arn
  task_role_arn      = aws_iam_role.ecs_task_role.arn
  
  # Container definition
  container_definitions = jsonencode([
    {
      name  = "raibu-backend"
      image = "${aws_ecr_repository.raibu_backend.repository_url}:latest"
      
      # Port mapping for Spring Boot application
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
          appProtocol   = "http"  # Helps ALB understand the protocol
        }
      ]
      
      # Environment variables for application configuration
      environment = [
        {
          name  = "DATABASE_URL"
          value = "jdbc:postgresql://${aws_db_instance.raibu_db.endpoint}/raibu"
        },
        {
          name  = "DATABASE_USERNAME"
          value = var.db_username
        },
        {
          name  = "PORT"
          value = "8080"
        },
        {
          name  = "SPRING_PROFILES_ACTIVE"
          value = var.environment
        }
      ]
      
      # Secrets from AWS Secrets Manager
      secrets = [
        {
          name      = "DATABASE_PASSWORD"
          valueFrom = aws_secretsmanager_secret.db_password.arn
        }
      ]
      
      # Logging configuration
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs_logs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "backend"
        }
      }
      
      # Health check to ensure container is running properly
      healthCheck = {
        command     = ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8080/ping || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60  # Give the app 60 seconds to start before health checks
      }
      
      # Essential container - if this fails, the task is stopped
      essential = true
    }
  ])
  
  tags = {
    Name        = "${var.project_name}-task"
    Environment = var.environment
  }
}

# ============================================
# Application Load Balancer (ALB)
# ============================================

resource "aws_lb" "raibu_alb" {
  name               = "${var.project_name}-alb-${var.environment}"
  internal           = false  # Internet-facing load balancer
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = [
    aws_subnet.public_subnet_1.id,
    aws_subnet.public_subnet_2.id
  ]
  
  # Enable deletion protection in production
  enable_deletion_protection = var.environment == "prod" ? true : false
  
  # Enable access logs (optional, requires S3 bucket)
  # access_logs {
  #   bucket  = "raibu-alb-logs"
  #   enabled = true
  # }
  
  tags = {
    Name        = "${var.project_name}-alb"
    Environment = var.environment
  }
}

# Target Group - defines how ALB forwards traffic to ECS tasks
resource "aws_lb_target_group" "raibu_tg" {
  name        = "${var.project_name}-tg-${var.environment}"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.raibu_vpc.id
  target_type = "ip"  # Required for Fargate
  
  # Health check configuration
  health_check {
    path                = "/ping"
    healthy_threshold   = 2  # 2 successful checks = healthy
    unhealthy_threshold = 3  # 3 failed checks = unhealthy
    timeout             = 5
    interval            = 30
    matcher             = "200"  # Expected HTTP status code
  }
  
  # Deregistration delay - wait time before removing unhealthy targets
  deregistration_delay = 30
  
  # Sticky sessions for WebSocket connections
  # Ensures a user stays connected to the same ECS task
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400  # 24 hours
    enabled         = true
  }
  
  tags = {
    Name        = "${var.project_name}-tg"
    Environment = var.environment
  }
}

# ALB Listener for HTTP traffic
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.raibu_alb.arn
  port              = 80
  protocol          = "HTTP"
  
  # Default action - forward all traffic to the target group
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.raibu_tg.arn
  }
}

# TODO: Add HTTPS listener with ACM certificate for production
# resource "aws_lb_listener" "https" {
#   load_balancer_arn = aws_lb.raibu_alb.arn
#   port              = 443
#   protocol          = "HTTPS"
#   ssl_policy        = "ELBSecurityPolicy-2016-08"
#   certificate_arn   = "arn:aws:acm:region:account-id:certificate/certificate-id"
#   
#   default_action {
#     type             = "forward"
#     target_group_arn = aws_lb_target_group.raibu_tg.arn
#   }
# }

# ============================================
# ECS Service
# ============================================

resource "aws_ecs_service" "raibu_service" {
  name            = "${var.project_name}-service"
  cluster         = aws_ecs_cluster.raibu_cluster.id
  task_definition = aws_ecs_task_definition.raibu_task.arn
  desired_count   = var.ecs_desired_count  # Number of tasks to run
  launch_type     = "FARGATE"
  
  # Platform version - use latest for best features and security
  platform_version = "LATEST"
  
  # Deployment configuration
  deployment_minimum_healthy_percent = 100  # Keep all tasks running during deployment
  deployment_maximum_percent         = 200  # Allow 2x tasks during deployment
  
  # Network configuration for Fargate tasks
  network_configuration {
    subnets = [
      aws_subnet.public_subnet_1.id,
      aws_subnet.public_subnet_2.id
    ]
    security_groups  = [aws_security_group.ecs_sg.id]
    assign_public_ip = true  # Required for pulling images from ECR
  }
  
  # Connect ECS service to the load balancer
  load_balancer {
    target_group_arn = aws_lb_target_group.raibu_tg.arn
    container_name   = "raibu-backend"
    container_port   = 8080
  }
  
  # Wait for the load balancer to be ready before creating the service
  depends_on = [
    aws_lb_listener.http
  ]
  
  tags = {
    Name        = "${var.project_name}-service"
    Environment = var.environment
  }
}

# ============================================
# Auto Scaling Configuration (Optional)
# ============================================

# Auto Scaling Target - links ECS service to auto scaling
resource "aws_appautoscaling_target" "ecs_target" {
  max_capacity       = var.ecs_max_capacity
  min_capacity       = var.ecs_min_capacity
  resource_id        = "service/${aws_ecs_cluster.raibu_cluster.name}/${aws_ecs_service.raibu_service.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

# Auto Scaling Policy - scale based on CPU utilization
resource "aws_appautoscaling_policy" "ecs_cpu_scaling" {
  name               = "${var.project_name}-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs_target.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target.service_namespace
  
  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 70.0  # Scale when CPU > 70%
    scale_in_cooldown  = 300   # Wait 5 minutes before scaling in
    scale_out_cooldown = 60    # Wait 1 minute before scaling out
  }
}

# Auto Scaling Policy - scale based on memory utilization
resource "aws_appautoscaling_policy" "ecs_memory_scaling" {
  name               = "${var.project_name}-memory-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs_target.resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_target.service_namespace
  
  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
    target_value       = 80.0  # Scale when memory > 80%
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}

# Outputs
output "alb_dns_name" {
  value       = aws_lb.raibu_alb.dns_name
  description = "Load Balancer DNS"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.raibu_backend.repository_url
}

output "db_endpoint" {
  value     = aws_db_instance.raibu_db.endpoint
  sensitive = true
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.raibu_cluster.name
}

output "ecs_service_name" {
  value = aws_ecs_service.raibu_service.name
}
