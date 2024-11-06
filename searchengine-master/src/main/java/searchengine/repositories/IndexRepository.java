package searchengine.repositories;

import java.util.HashMap;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;

public interface IndexRepository extends CrudRepository<Index, Integer> {
  @Query(value = "SELECT * FROM lemma_index where lemma = ?1", nativeQuery = true)
  public Index findIndexByLemma(String path);
  @Modifying
  @Transactional
  @Query(value = "DELETE FROM lemma_index where page_id = ?1", nativeQuery = true)
  public void deleteIndexByPageId(int id);
  @Query(value = "SELECT * FROM lemma_index where page_id = ?1", nativeQuery = true)
  public List<Index> findIndexByPageId(int id);
  @Query(value = "select id FROM lemma_index where lemma_id = ?1", nativeQuery = true)
  public int findLemmaIndexByLemmaId(String lemma);
  @Query(value = "SELECT page_id FROM lemma_index where lemma_id = ?1", nativeQuery = true)
  public List<Integer> findPageIdByLemmaId(int id);
  @Query(value = "SELECT lemma_rank FROM lemma_index where page_id = ?1", nativeQuery = true)
  public List<Integer> findAllRanksByPageId(int id);
}
