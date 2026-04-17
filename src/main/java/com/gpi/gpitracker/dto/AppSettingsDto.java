package com.gpi.gpitracker.dto;

import java.util.List;

public class AppSettingsDto {

    private Double       montantMax;
    private Double       montantMin;
    private List<String> devisesAutorisees;  // ["EUR","USD","GBP"]
    private List<String> paysSanctionnes;    // ["IR","KP","SY"]
    private Integer      delaiTraitementH;

    // ── Getters / Setters ──────────────────────────────────────────

    public Double getMontantMax()                          { return montantMax; }
    public void   setMontantMax(Double v)                  { this.montantMax = v; }

    public Double getMontantMin()                          { return montantMin; }
    public void   setMontantMin(Double v)                  { this.montantMin = v; }

    public List<String> getDevisesAutorisees()             { return devisesAutorisees; }
    public void         setDevisesAutorisees(List<String> v){ this.devisesAutorisees = v; }

    public List<String> getPaysSanctionnes()               { return paysSanctionnes; }
    public void         setPaysSanctionnes(List<String> v) { this.paysSanctionnes = v; }

    public Integer getDelaiTraitementH()                   { return delaiTraitementH; }
    public void    setDelaiTraitementH(Integer v)          { this.delaiTraitementH = v; }
}