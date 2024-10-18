package searchengine.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lemma")
public class Lemma {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  int id;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "site_id", nullable = false)
  ModelSite ModelSite;
  @Column(columnDefinition = "VARCHAR (255)", nullable = false)
  String lemma;
  @Column(columnDefinition = "INT", nullable = false)
  int frequency;
}
