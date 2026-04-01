package com.gpi.gpitracker.dto;

public class AdminProfileDto {

    private String keycloakId;
    private String language;
    private String timezone;
    private String theme;
    private String dateFormat;
    private boolean notifUserAdd;
    private boolean notifUserDelete;
    private boolean notifStatusChange;
    private boolean notifSystemAlert;

    public String getKeycloakId()                { return keycloakId; }
    public void   setKeycloakId(String v)        { this.keycloakId = v; }

    public String getLanguage()                  { return language; }
    public void   setLanguage(String v)          { this.language = v; }

    public String getTimezone()                  { return timezone; }
    public void   setTimezone(String v)          { this.timezone = v; }

    public String getTheme()                     { return theme; }
    public void   setTheme(String v)             { this.theme = v; }

    public String getDateFormat()                { return dateFormat; }
    public void   setDateFormat(String v)        { this.dateFormat = v; }

    public boolean isNotifUserAdd()              { return notifUserAdd; }
    public void    setNotifUserAdd(boolean v)    { this.notifUserAdd = v; }

    public boolean isNotifUserDelete()           { return notifUserDelete; }
    public void    setNotifUserDelete(boolean v) { this.notifUserDelete = v; }

    public boolean isNotifStatusChange()         { return notifStatusChange; }
    public void    setNotifStatusChange(boolean v){ this.notifStatusChange = v; }

    public boolean isNotifSystemAlert()          { return notifSystemAlert; }
    public void    setNotifSystemAlert(boolean v){ this.notifSystemAlert = v; }
}