package it.mscuttari.examples.generic;

import it.mscuttari.kaoldb.annotations.Column;
import it.mscuttari.kaoldb.annotations.Entity;
import it.mscuttari.kaoldb.annotations.Id;
import it.mscuttari.kaoldb.annotations.Table;

@Entity
@Table(name = "b")
public class EntityB {

    @Id
    @Column(name = "id")
    public int id;

}