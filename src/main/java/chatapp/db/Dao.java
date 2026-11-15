package chatapp.db;

import java.sql.SQLException;
import java.util.Optional;

/** GENERIC INTERFACE: every DAO can load its entity type T by id. */
public interface Dao<T> {
    Optional<T> findById(long id) throws SQLException;
}
