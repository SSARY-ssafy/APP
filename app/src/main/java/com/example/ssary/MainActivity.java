// MainActivity.java
package com.example.ssary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import java.util.ArrayList;
import java.util.List;

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
        TextView calendarButton = findViewById(R.id.calender_button);
        calendarButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
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

    // Firebase에서 채용 공고 데이터를 로드하는 메서드
    private void loadJobDataFromFirebase() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("JOB");
        Query query = databaseReference.orderByChild("endDate").limitToFirst(12);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                jobList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Job job = snapshot.getValue(Job.class);
                    if (job != null) {
                        jobList.add(job);
                    }
                }
                mainJobAdapter = new MainJobAdapter(jobList); // jobList를 사용해 Adapter 초기화
                recruitViewPager.setAdapter(mainJobAdapter); // ViewPager에 새로운 Adapter 설정
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseData", "Failed to load data.", databaseError.toException());
            }
        });
    }
}
