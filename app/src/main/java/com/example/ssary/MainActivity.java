// MainActivity.java
package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CalendarView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 recruitViewPager;
    private MainJobAdapter mainJobAdapter;
    private List<Job> jobList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ViewPager2와 Adapter 설정
        recruitViewPager = findViewById(R.id.recruit_view_pager);
        mainJobAdapter = new MainJobAdapter(jobList);  // 초기에는 빈 jobList로 설정
        recruitViewPager.setAdapter(mainJobAdapter);

        // Firebase에서 채용 공고 데이터를 불러오기
        loadJobDataFromFirebase();

        // 첫 번째 공간 클릭 시 이동
        FrameLayout firstSection = findViewById(R.id.first_section);
        firstSection.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // 캘린더 이동 버튼 클릭 시
        TextView calendarButton = findViewById(R.id.calendar_button);
        calendarButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
            startActivity(intent);
        });

        // 다이어리 이동 버튼 클릭 시
        TextView diaryButton = findViewById(R.id.diary_button);
        diaryButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, MyPageActivity.class);
            startActivity(intent);
        });

        // 채용 공고 이동 버튼 클릭 시
        TextView recruitButton = findViewById(R.id.recruit_button);
        recruitButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, JobActivity.class);
            startActivity(intent);
        });

        // CalendarView 이벤트 설정
        CalendarView calendarView = findViewById(R.id.calendar_view);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String date = year + "/" + (month + 1) + "/" + dayOfMonth;
            Toast.makeText(MainActivity.this, "선택한 날짜: " + date, Toast.LENGTH_SHORT).show();
        });
    }

    private void loadJobDataFromFirebase() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("JOB");
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                jobList.clear();
                Date today = new Date();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Job job = snapshot.getValue(Job.class);
                    if (job != null) {
                        try {
                            Date endDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(job.getEndDate());
                            if (endDate != null && endDate.after(today)) { // 마감일이 오늘 이후인 공고만 추가
                                jobList.add(job);
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // 최대 4개의 공고만 표시
                if (jobList.size() > 4) {
                    jobList = jobList.subList(0, 4);
                }

                // ViewPager 및 "공고 없음" 메시지 제어
                ViewPager2 recruitViewPager = findViewById(R.id.recruit_view_pager);
                TextView noJobsText = findViewById(R.id.no_jobs_text);

                if (jobList.isEmpty()) {
                    recruitViewPager.setVisibility(View.GONE);
                    noJobsText.setVisibility(View.VISIBLE);
                } else {
                    recruitViewPager.setVisibility(View.VISIBLE);
                    noJobsText.setVisibility(View.GONE);
                }

                // 어댑터 설정
                mainJobAdapter = new MainJobAdapter(jobList);
                recruitViewPager.setAdapter(mainJobAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("MainActivity", "Error fetching data: " + databaseError.getMessage());
            }
        });
    }


}
