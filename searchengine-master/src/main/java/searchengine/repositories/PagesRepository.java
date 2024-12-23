package searchengine.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.ModelPage;

public interface PagesRepository extends CrudRepository<ModelPage, Integer> {
  @Query(value = "SELECT * FROM page where path = ?1", nativeQuery = true)
  public ModelPage findByPath(String path);
  @Query(value = "SELECT *  FROM page where site_id = ?1", nativeQuery = true)
  public ModelPage findBySiteId(int id);
  @Modifying
  @Transactional
  @Query(value = "DELETE FROM page where site_id = ?1", nativeQuery = true)
  public void deleteBySiteId(int id);
  @Query(value = "select count(*) FROM page where site_id = ?1", nativeQuery = true)
  public int countBySiteId(int id);
  @Query(value = "select * FROM page where path = ?1 AND site_id = ?2", nativeQuery = true)
  public ModelPage findByUrlAndId(String url, int id);
  @Query(value = "SELECT id FROM page where site_id = ?1", nativeQuery = true)
  public List<Integer> findAllPagesIdsBySiteId(int id);
  @Query(value = "SELECT p.id as pid, p.* FROM page p where p.id in ?1", nativeQuery = true)
  public List<ModelPage> findAllPagesByPageIds(List<Integer> ids);



}
