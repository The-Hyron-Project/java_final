package searchengine.model;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Site {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  int id;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  Status status;
  @Column(name="status_time")
  Date statusTime;
  @Column(name="last_error", columnDefinition = "TEXT")
  String lastError;
  @Column(columnDefinition = "VARCHAR (255)", nullable = false)
  String url;
  @Column(columnDefinition = "VARCHAR (255)", nullable = false)
  String name;
}


