package server.attribute.status.model.derived;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import server.attribute.status.model.Status;
import server.attribute.status.types.StatusTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Data
@Serdeable
@JsonTypeName("UNCONSCIOUS")
@EqualsAndHashCode(callSuper = false)
public class Unconscious extends Status {

    public Unconscious() {
        this.setDerivedEffects(new HashMap<>());
        this.setStatusEffects(defaultStatusEffects());
        this.setExpiration(null);
        this.setCanStack(false);
        this.setOrigin(null);
        this.setCategory(StatusTypes.UNCONSCIOUS.getType());
    }

    public Set<String> defaultStatusEffects() {
        return new HashSet<>(Set.of(
                StatusTypes.CANNOT_ACT.getType(),
                StatusTypes.CANNOT_ATTACK.getType(),
                StatusTypes.CANNOT_CAST.getType(),
                StatusTypes.CANNOT_MOVE.getType()
        ));
    }
}
