package com.example.ssary;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JobsAdapter extends RecyclerView.Adapter<JobsAdapter.JobViewHolder> {

    private List<Job> jobsList;
    private Context context;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private OnItemClickListener onItemClickListener; // 클릭 리스너 인터페이스 선언

    public JobsAdapter(List<Job> jobsList, Context context) {
        this.jobsList = jobsList;
        this.context = context;
    }

    // 아이템 클릭 리스너 인터페이스 정의
    public interface OnItemClickListener {
        void onItemClick(Job job);
    }

    // 아이템 클릭 리스너 설정 메서드
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void updateJobs(List<Job> newJobsList) {
        this.jobsList = newJobsList;
        notifyDataSetChanged();
    }

    @Override
    public JobViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.job_item_layout, parent, false);
        return new JobViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(JobViewHolder holder, int position) {
        Job job = jobsList.get(position);
        holder.companyName.setText("# " + job.getCompanyName());

        // 마감 여부에 따른 텍스트 색상 설정
        try {
            Date endDate = sdf.parse(job.getEndDate());
            Date today = new Date();

            if (endDate != null && endDate.before(today)) {
                holder.companyName.setTextColor(Color.GRAY);
            } else {
                holder.companyName.setTextColor(Color.BLACK);
            }
        } catch (ParseException e) {
            e.printStackTrace();
            holder.companyName.setTextColor(Color.BLACK);
        }

        // 아이템 클릭 시 리스너 호출
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(job); // 리스너의 onItemClick 메서드 호출
            }
        });
    }

    @Override
    public int getItemCount() {
        return jobsList.size();
    }

    public static class JobViewHolder extends RecyclerView.ViewHolder {
        public TextView companyName;

        public JobViewHolder(View view) {
            super(view);
            companyName = view.findViewById(R.id.company_name);
        }
    }
}
