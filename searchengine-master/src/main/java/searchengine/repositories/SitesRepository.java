package searchengine.repositories;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import searchengine.model.ModelSite;
import searchengine.model.Status;

public interface SitesRepository extends CrudRepository<ModelSite, Integer> {
  @Query(value = "SELECT * FROM site where name = ?1", nativeQuery = true)
  public ModelSite findByName(String name);
  @Query(value = "SELECT id FROM site where name = ?1", nativeQuery = true)
  public String findUrlByName(int id);
  @Query(value = "SELECT * FROM site where status = ?1", nativeQuery = true)
  public List<ModelSite> findAllByStatus(String status);
  @Query(value = "SELECT id FROM site where url = ?1", nativeQuery = true)
  public Integer findIdByUrl(String status);
}
