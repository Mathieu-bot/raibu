package shi.raibu.shi.conf;

import org.springframework.test.context.DynamicPropertyRegistry;
import shi.raibu.shi.PojaGenerated;

@PojaGenerated
public class BucketConf {

  void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("aws.s3.bucket", () -> "dummy-bucket");
  }
}
