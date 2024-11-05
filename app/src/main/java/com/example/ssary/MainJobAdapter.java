package com.example.ssary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainJobAdapter extends RecyclerView.Adapter<MainJobAdapter.JobPageViewHolder> {

    private List<List<Job>> jobPages;

    public MainJobAdapter(List<Job> jobList) {
        jobPages = new ArrayList<>();
        // 데이터를 4개씩 묶어 페이지별로 나눕니다.
        for (int i = 0; i < jobList.size(); i += 4) {
            int end = Math.min(i + 4, jobList.size());
            jobPages.add(jobList.subList(i, end));
        }
    }

    @NonNull
    @Override
    public JobPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job_page, parent, false);
        return new JobPageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobPageViewHolder holder, int position) {
        List<Job> jobsForPage = jobPages.get(position);
        holder.bind(jobsForPage);
    }

    @Override
    public int getItemCount() {
        return jobPages.size();
    }

    static class JobPageViewHolder extends RecyclerView.ViewHolder {

        private TextView[] tvDDayArray = new TextView[4];
        private TextView[] tvCompanyNameArray = new TextView[4];
        private TextView[] tvJobPositionArray = new TextView[4];
        private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        public JobPageViewHolder(@NonNull View itemView) {
            super(itemView);
            // 각 아이템 뷰를 배열로 관리
            tvDDayArray[0] = itemView.findViewById(R.id.tv_d_day_1);
            tvCompanyNameArray[0] = itemView.findViewById(R.id.tv_company_name_1);
            tvJobPositionArray[0] = itemView.findViewById(R.id.tv_job_position_1);

            tvDDayArray[1] = itemView.findViewById(R.id.tv_d_day_2);
            tvCompanyNameArray[1] = itemView.findViewById(R.id.tv_company_name_2);
            tvJobPositionArray[1] = itemView.findViewById(R.id.tv_job_position_2);

            tvDDayArray[2] = itemView.findViewById(R.id.tv_d_day_3);
            tvCompanyNameArray[2] = itemView.findViewById(R.id.tv_company_name_3);
            tvJobPositionArray[2] = itemView.findViewById(R.id.tv_job_position_3);

            tvDDayArray[3] = itemView.findViewById(R.id.tv_d_day_4);
            tvCompanyNameArray[3] = itemView.findViewById(R.id.tv_company_name_4);
            tvJobPositionArray[3] = itemView.findViewById(R.id.tv_job_position_4);
        }

        public void bind(List<Job> jobsForPage) {
            for (int i = 0; i < 4; i++) {
                if (i < jobsForPage.size()) {
                    Job job = jobsForPage.get(i);
                    int daysRemaining = calculateDaysRemaining(job.getEndDate());
                    tvDDayArray[i].setText("D-" + daysRemaining);
                    tvCompanyNameArray[i].setText(job.getCompanyName());
                    tvJobPositionArray[i].setText(job.getJobPosition());
                } else {
                    tvDDayArray[i].setText("");
                    tvCompanyNameArray[i].setText("");
                    tvJobPositionArray[i].setText("");
                }
            }
        }

        private int calculateDaysRemaining(String endDate) {
            try {
                Date jobEndDate = dateFormat.parse(endDate);
                if (jobEndDate == null) return -1;
                Date currentDate = new Date();
                long diffInMillis = jobEndDate.getTime() - currentDate.getTime();
                return (int) TimeUnit.MILLISECONDS.toDays(diffInMillis);
            } catch (ParseException e) {
                e.printStackTrace();
                return -1;
            }
        }
    }
}
