package shi.raibu.shi.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import shi.raibu.shi.endpoint.rest.dto.FlaggedUserDto;
import shi.raibu.shi.model.Report;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {
  List<Report> findByReportedUserId(String reportedUserId);

  List<Report> findByStatus(Report.ReportStatus status);

  /** Users reported by at least {@code minReporters} distinct reporters since {@code since}. */
  @Query(
      "select new shi.raibu.shi.endpoint.rest.dto.FlaggedUserDto("
          + "r.reportedUserId, count(distinct r.reporterId), count(r), max(r.createdAt)) "
          + "from Report r where r.createdAt >= :since "
          + "group by r.reportedUserId "
          + "having count(distinct r.reporterId) >= :minReporters "
          + "order by count(distinct r.reporterId) desc, max(r.createdAt) desc")
  List<FlaggedUserDto> findFlaggedUsers(
      @Param("since") Instant since, @Param("minReporters") long minReporters);

  /** A null {@code sessionId} matches reports without a session ({@code is null}). */
  boolean existsByReporterIdAndReportedUserIdAndSessionId(
      String reporterId, String reportedUserId, String sessionId);
}
