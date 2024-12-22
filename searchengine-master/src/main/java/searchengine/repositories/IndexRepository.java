package searchengine.repositories;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;

public interface IndexRepository extends CrudRepository<Index, Integer> {
  @Modifying
  @Transactional
  @Query(value = "DELETE FROM lemma_index where page_id = ?1", nativeQuery = true)
  public void deleteIndexByPageId(int id);
  @Query(value = "SELECT * FROM lemma_index where page_id = ?1", nativeQuery = true)
  public List<Index> findIndexByPageId(int id);
  @Query(value = "SELECT page_id FROM lemma_index where lemma_id = ?1", nativeQuery = true)
  public List<Integer> findPageIdByLemmaId(int id);
  @Query(value = "SELECT l.page_id, SUM(l.lemma_rank) AS sum FROM lemma_index AS l where l.page_id in ?1 and lemma_id in ?2 group by l.page_id" , nativeQuery = true)
  public List<String> findSumRanksByPageIdList(List<Integer> ids, ArrayList<Integer> lemmaIds);
}
