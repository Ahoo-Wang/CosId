package me.ahoo.cosid.mysql;

import com.google.common.base.Strings;
import me.ahoo.cosid.CosIdException;

/**
 * @author ahoo wang
 */
public class NameMissingException extends CosIdException {

    private final String name;

    /**
     * Constructs a new runtime exception with {@code null} as its
     * detail message.  The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause}.
     */
    public NameMissingException(String name) {
        super(Strings.lenientFormat("name:[%s] missing.", name));
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
