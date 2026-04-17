package com.gpi.gpitracker.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "APP_SETTINGS")
public class AppSettings {

    @Id
    @Column(name = "ID")
    private String id = "GLOBAL"; // une seule ligne de config

    // Plafond montant
    @Column(name = "MONTANT_MAX")
    private Double montantMax = 50000.0;

    @Column(name = "MONTANT_MIN")
    private Double montantMin = 1.0;

    // Devises autorisées (séparées par virgule : "EUR,USD,GBP,TND")
    @Column(name = "DEVISES_AUTORISEES", length = 500)
    private String devisesAutorisees = "EUR,USD,GBP,TND";

    // Pays sanctionnés (codes ISO séparés par virgule : "IR,KP,SY,RU")
    @Column(name = "PAYS_SANCTIONNES", length = 2000)
    private String paysSanctionnes = "IR,KP,SY";

    // Délai de traitement en heures
    @Column(name = "DELAI_TRAITEMENT_H")
    private Integer delaiTraitementH = 48;

    // ── Getters / Setters ──────────────────────────────────────────

    public String getId()                     { return id; }
    public void   setId(String v)             { this.id = v; }

    public Double getMontantMax()             { return montantMax; }
    public void   setMontantMax(Double v)     { this.montantMax = v; }

    public Double getMontantMin()             { return montantMin; }
    public void   setMontantMin(Double v)     { this.montantMin = v; }

    public String getDevisesAutorisees()      { return devisesAutorisees; }
    public void   setDevisesAutorisees(String v){ this.devisesAutorisees = v; }

    public String getPaysSanctionnes()        { return paysSanctionnes; }
    public void   setPaysSanctionnes(String v){ this.paysSanctionnes = v; }

    public Integer getDelaiTraitementH()      { return delaiTraitementH; }
    public void    setDelaiTraitementH(Integer v){ this.delaiTraitementH = v; }
}