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
@Table(name = "lemma_index")
public class Index {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  int id;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "page_id", nullable = false)
  ModelPage modelPage;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lemma_id", nullable = false)
  Lemma lemma;
  @Column(name = "lemma_rank", columnDefinition = "float", nullable = false)
  Float rank;
}
