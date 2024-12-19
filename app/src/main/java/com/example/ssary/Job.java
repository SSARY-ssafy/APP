package com.example.ssary;

import com.google.firebase.database.PropertyName;

public class Job {

    private int jobNumber;
    private String companyName;
    private String startDate;
    private String endDate;
    private String jobPosition;
    private String jobSite;

    public Job() {

    }

    @PropertyName("순번")
    public int getJobNumber() {
        return jobNumber;
    }

    @PropertyName("순번")
    public void setJobNumber(int jobNumber) {
        this.jobNumber = jobNumber;
    }

    @PropertyName("기업명")
    public String getCompanyName() {
        return companyName;
    }

    @PropertyName("기업명")
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    @PropertyName("제출시작일")
    public String getStartDate() {
        return startDate;
    }

    @PropertyName("제출시작일")
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @PropertyName("제출마감일")
    public String getEndDate() {
        return endDate;
    }

    @PropertyName("제출마감일")
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @PropertyName("채용직무")
    public String getJobPosition() {
        return jobPosition;
    }

    @PropertyName("채용직무")
    public void setJobPosition(String jobPosition) {
        this.jobPosition = jobPosition;
    }

    @PropertyName("채용공고 사이트")
    public String getJobSite() {
        return jobSite;
    }

    @PropertyName("채용공고 사이트")
    public void setJobSite(String jobSite) {
        this.jobSite = jobSite;
    }
}
