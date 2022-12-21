package software.netcore.radman.data.radius.repo;

import org.springframework.data.jpa.repository.Query;
import software.netcore.radman.data.radius.entity.RadCheck;
import software.netcore.radman.data.radius.spec.RadiusRepository;

import java.util.List;
import java.util.Set;

/**
 * @since v. 1.0.0
 */
public interface RadCheckRepo extends RadiusRepository<RadCheck, Integer> {

    List<RadCheck> findAll();

    void deleteAllByUsername(String username);

    // JPA to delete records by their associated username, operation and value.  JPA's are implicitly declared and
    // are directly related to their database schema
    void deleteByUsernameAndAttributeAndOpAndValue(String username, String attribute, String op, String value);

    // Modify to delete including ID as part of the lookup (preventing deletion of all records for a user)
    void deleteByIdAndUsernameAndAttribute(Integer Id, String name, String attribute);

    void deleteAllByAttribute(String attribute);

    @Query("SELECT r.username FROM RadCheck r ORDER BY r.username")
    Set<String> getUsernames();

    @Query("SELECT r.attribute FROM RadCheck r ORDER BY r.attribute")
    Set<String> getAttributes();

}
