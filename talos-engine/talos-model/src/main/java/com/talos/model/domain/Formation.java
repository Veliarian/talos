package com.talos.model.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical composite formation structure representing military command tree.
 */
@Getter
@Setter
@NoArgsConstructor
public class Formation {

    private String id;
    private String name;
    private String side;
    private String parentFormationId;

    private List<Formation> subFormations = new ArrayList<>();
    private List<String> unitIds = new ArrayList<>();

    public Formation(String id, String name, String side) {
        this.id = id;
        this.name = name;
        this.side = side;
    }
}