package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;

public interface IndexRepository extends CrudRepository<Index, Integer> {
  @Query(value = "SELECT * FROM lemma_index where lemma = ?1", nativeQuery = true)
  public Lemma findIndexByLemma(String path);
  @Modifying
  @Transactional
  @Query(value = "DELETE FROM lemma_index where page_id = ?1", nativeQuery = true)
  public void deleteIndexByPageId(int id);
}
