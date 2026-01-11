package shi.raibu.shi.conf;

import org.springframework.test.context.DynamicPropertyRegistry;
import shi.raibu.shi.PojaGenerated;

@PojaGenerated
public class EmailConf {

  void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("aws.ses.source", () -> "dummy-ses-source");
  }
}
