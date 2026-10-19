package com.cardbench.disputes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cardmembers")
public class Cardmember {
    @Id
    private String id;
    private String name;
    private String email;

    protected Cardmember() {
    }

    public Cardmember(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
