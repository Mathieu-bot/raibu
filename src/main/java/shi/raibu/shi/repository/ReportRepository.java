package shi.raibu.shi.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.model.Report;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {
  List<Report> findByReportedUserId(String reportedUserId);

  List<Report> findByStatus(Report.ReportStatus status);
}
