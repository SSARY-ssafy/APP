package com.example.ssary;

import com.google.firebase.database.PropertyName;

public class Job {
    private String companyName;
    private String startDate;
    private String endDate;
    private String jobPosition;
    private String jobSite;

    public Job() {
        // Default constructor required for calls to DataSnapshot.getValue(Job.class)
    }

    // Firebase에서 '기업명' 필드를 'companyName' 필드로 매핑
    @PropertyName("기업명")
    public String getCompanyName() {
        return companyName;
    }

    @PropertyName("기업명")
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    // Firebase에서 '제출시작일' 필드를 'startDate' 필드로 매핑
    @PropertyName("제출시작일")
    public String getStartDate() {
        return startDate;
    }

    @PropertyName("제출시작일")
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    // Firebase에서 '제출마감일' 필드를 'endDate' 필드로 매핑
    @PropertyName("제출마감일")
    public String getEndDate() {
        return endDate;
    }

    @PropertyName("제출마감일")
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    // Firebase에서 '채용직무' 필드를 'jobPosition' 필드로 매핑
    @PropertyName("채용직무")
    public String getJobPosition() {
        return jobPosition;
    }

    @PropertyName("채용직무")
    public void setJobPosition(String jobPosition) {
        this.jobPosition = jobPosition;
    }

    // Firebase에서 '채용공고사이트' 필드를 'jobSite' 필드로 매핑
    @PropertyName("채용공고 사이트")
    public String getJobSite() {
        return jobSite;
    }

    @PropertyName("채용공고 사이트")
    public void setJobSite(String jobSite) {
        this.jobSite = jobSite;
    }
}
