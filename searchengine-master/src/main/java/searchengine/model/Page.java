package searchengine.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(indexes = @Index(columnList = "path"))
public class Page {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  int id;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "site_id", nullable = false)
  Site Site;
  @Column(columnDefinition = "VARCHAR (999)", nullable = false)
  String path;
  @Column(nullable = false)
  int code;
  @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
  String content;

  protected Page (){}
}
