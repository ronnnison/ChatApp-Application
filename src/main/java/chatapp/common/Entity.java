package chatapp.common;

import java.io.Serializable;

/** INHERITANCE: abstract base class for every object that has a database id. */
public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    protected final long id;

    protected Entity(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
