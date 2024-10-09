package searchengine.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "site")
public class ModelSite {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  int id;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  Status status;
  @Column(name="status_time")
  LocalDateTime statusTime;
  @Column(name="last_error", columnDefinition = "TEXT")
  String lastError;
  @Column(columnDefinition = "VARCHAR (255)", nullable = false)
  String url;
  @Column(columnDefinition = "VARCHAR (255)", nullable = false)
  String name;
}


