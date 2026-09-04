package com.lubrimanager.common.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compteur persistant par « portée » (ex. {@code CLI}, {@code RECH-20260903}).
 * Verrouillé en écriture le temps de l'incrément pour garantir des références
 * uniques et strictement croissantes, même sous forte concurrence.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "reference_counter")
public class ReferenceCounter {

    @Id
    @Column(length = 40)
    private String scope;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    public ReferenceCounter(String scope, long nextValue) {
        this.scope = scope;
        this.nextValue = nextValue;
    }

    public long increment() {
        return ++this.nextValue;
    }
}
