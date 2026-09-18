package com.talos.model.domain;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Formation {
    private String id;
    private String name;
    private String side;
    private String parentFormationId;

    private List<Formation> subFormations = new ArrayList<>();

    private List<String> unitIds = new ArrayList<>();
}