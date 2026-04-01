package com.gpi.gpitracker.entity;


import jakarta.persistence.*;


@Entity
@Table(name = "admin_profiles")
public class AdminProfile {

    @Id
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    @Column(name = "language", length = 10)
    private String language = "fr";

    @Column(name = "timezone", length = 60)
    private String timezone = "Africa/Tunis";

    @Column(name = "theme", length = 20)
    private String theme = "light";

    @Column(name = "date_format", length = 20)
    private String dateFormat = "dd/MM/yyyy";

    @Column(name = "notif_user_add")
    private boolean notifUserAdd = true;

    @Column(name = "notif_user_delete")
    private boolean notifUserDelete = true;

    @Column(name = "notif_status_change")
    private boolean notifStatusChange = true;

    @Column(name = "notif_system_alert")
    private boolean notifSystemAlert = true;

    public String getKeycloakId()               { return keycloakId; }
    public void   setKeycloakId(String v)       { this.keycloakId = v; }

    public String getLanguage()                 { return language; }
    public void   setLanguage(String v)         { this.language = v; }

    public String getTimezone()                 { return timezone; }
    public void   setTimezone(String v)         { this.timezone = v; }

    public String getTheme()                    { return theme; }
    public void   setTheme(String v)            { this.theme = v; }

    public String getDateFormat()               { return dateFormat; }
    public void   setDateFormat(String v)       { this.dateFormat = v; }

    public boolean isNotifUserAdd()             { return notifUserAdd; }
    public void    setNotifUserAdd(boolean v)   { this.notifUserAdd = v; }

    public boolean isNotifUserDelete()          { return notifUserDelete; }
    public void    setNotifUserDelete(boolean v){ this.notifUserDelete = v; }

    public boolean isNotifStatusChange()        { return notifStatusChange; }
    public void    setNotifStatusChange(boolean v){ this.notifStatusChange = v; }

    public boolean isNotifSystemAlert()         { return notifSystemAlert; }
    public void    setNotifSystemAlert(boolean v){ this.notifSystemAlert = v; }
}