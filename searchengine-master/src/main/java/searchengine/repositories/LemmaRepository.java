package searchengine.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

public interface LemmaRepository extends CrudRepository<Lemma, Integer> {
  @Query(value = "SELECT * FROM lemma where lemma = ?1 and site_id = ?2", nativeQuery = true)
  public Lemma findLemmaByLemmaAndSiteId(String path, int site_id);
  @Modifying
  @Transactional
  @Query(value = "DELETE FROM lemma where lemma = ?1 and site_id = ?2", nativeQuery = true)
  public void deleteLemmaByLemmaAndSiteId(String path, int site_id);
  @Modifying
  @Transactional
  @Query(value = "DELETE FROM lemma where site_id = ?1", nativeQuery = true)
  public void deleteLemmaBySiteId(int site_id);
  @Query(value = "select count(*) FROM lemma where site_id = ?1", nativeQuery = true)
  public int countLemmasBySiteId(int id);
}
