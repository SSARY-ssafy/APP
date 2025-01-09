package com.example.ssary;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

        recruitViewPager = findViewById(R.id.recruit_view_pager);
        mainJobAdapter = new MainJobAdapter(jobList);
        recruitViewPager.setAdapter(mainJobAdapter);

        loadJobDataFromFirebase();

        FrameLayout firstSection = findViewById(R.id.first_section);
        firstSection.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            startActivity(intent);
        });

        TextView calendarButton = findViewById(R.id.calendar_button);
        calendarButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
            startActivity(intent);
        });

        TextView diaryButton = findViewById(R.id.diary_button);
        diaryButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, MyPageActivity.class);
            startActivity(intent);
        });

        TextView recruitButton = findViewById(R.id.recruit_button);
        recruitButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, JobActivity.class);
            startActivity(intent);
        });

        // 커스텀 달력 세팅
        setupCustomCalendar();
        loadTodaySchedules();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCustomCalendar();
        loadTodaySchedules();
    }




    private void setupCustomCalendar() {
        TextView monthYearText = findViewById(R.id.month_year_text);
        GridLayout calendarLayout = findViewById(R.id.custom_calendar);
        TextView scheduleHeader = findViewById(R.id.schedule_header);

        calendarLayout.removeAllViews();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidthPx = dm.widthPixels;

        int dayViewHorizontalPadding = (int) (screenWidthPx * 0.015f);
        int dayViewVerticalPadding   = (int) (screenWidthPx * 0.008f);

        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int today = calendar.get(Calendar.DAY_OF_MONTH);

        monthYearText.setText(year + "년 " + month + "월");

        SimpleDateFormat sdf = new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN);
        scheduleHeader.setText(sdf.format(calendar.getTime()));

        String[] daysOfWeek = {"S", "M", "T", "W", "T", "F", "S"};
        for (String day : daysOfWeek) {
            TextView dayView = new TextView(this);
            dayView.setText(day);
            dayView.setGravity(Gravity.CENTER);
            dayView.setTypeface(null, Typeface.BOLD);
            // 여기서 퍼센트 기반 패딩 적용
            dayView.setPadding(dayViewHorizontalPadding, dayViewVerticalPadding,
                    dayViewHorizontalPadding, dayViewVerticalPadding);

            calendarLayout.addView(dayView);
        }

        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 1; i < firstDayOfWeek; i++) {
            TextView emptyView = new TextView(this);
            emptyView.setText("");
            calendarLayout.addView(emptyView);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            TextView dateView = new TextView(this);
            dateView.setText(String.valueOf(day));
            dateView.setGravity(Gravity.CENTER);

            // 날짜 셀에도 퍼센트 기반 패딩 적용
            dateView.setPadding(dayViewHorizontalPadding, dayViewVerticalPadding,
                    dayViewHorizontalPadding, dayViewVerticalPadding);

            if (day == today) {
                dateView.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_background));
                dateView.setTextColor(Color.WHITE);
            }
            calendarLayout.addView(dateView);
        }
    }

    private void loadTodaySchedules() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        String uid = user.getUid();

        Calendar calendar = Calendar.getInstance();
        String dateKey = createValidDateKey(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        DatabaseReference scheduleRef = FirebaseDatabase.getInstance()
                .getReference("schedule")
                .child(uid)
                .child(dateKey);

        scheduleRef.orderByChild("order").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                LinearLayout scheduleContentArea = findViewById(R.id.schedule_content_area);

                scheduleContentArea.removeAllViews();

                // 데이터가 없으면 "오늘 일정이 없습니다." TextView를 새로 만들어 붙임
                if (!dataSnapshot.exists() || dataSnapshot.getChildrenCount() == 0) {
                    TextView noDataTV = new TextView(MainActivity.this);
                    noDataTV.setText("오늘 일정이 없습니다.");
                    noDataTV.setTextColor(Color.parseColor("#888888"));
                    noDataTV.setGravity(Gravity.CENTER);
                    noDataTV.setPadding(0, 16, 0, 16);
                    scheduleContentArea.addView(noDataTV);
                    return;
                }

                int count = 0;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String content = snapshot.child("content").getValue(String.class);
                    if (content == null) continue;

                    // 최대 4개의 스케줄만 표시
                    if (count == 4) {
                        TextView moreView = new TextView(MainActivity.this);
                        moreView.setText("...");
                        moreView.setTextSize(16f);
                        moreView.setTypeface(null, Typeface.BOLD);
                        moreView.setPadding(0, 8, 0, 8);
                        scheduleContentArea.addView(moreView);
                        break;
                    }

                    // (1) 스케줄 한 줄 레이아웃
                    LinearLayout scheduleItemLayout = new LinearLayout(MainActivity.this);
                    scheduleItemLayout.setOrientation(LinearLayout.HORIZONTAL);
                    scheduleItemLayout.setGravity(Gravity.CENTER_VERTICAL);
                    // 너비 match_parent, 높이 wrap_content
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    scheduleItemLayout.setLayoutParams(itemParams);
                    // 조금의 세로 여백
                    scheduleItemLayout.setPadding(0, 8, 0, 8);

                    // (2) 파란 네모박스
                    View blueBox = new View(MainActivity.this);
                    // 16dp -> px 변환
                    int boxSize = (int) (16 * getResources().getDisplayMetrics().density);

                    LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(boxSize, boxSize);
                    // 박스와 텍스트 사이 가로 간격
                    boxParams.setMarginEnd(8);

                    blueBox.setLayoutParams(boxParams);
                    blueBox.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.square_blue));
                    blueBox.setPadding(0, 2, 0, 0);

                    // (3) 내용 TextView
                    TextView contentView = new TextView(MainActivity.this);
                    contentView.setLayoutParams(
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                    );
                    contentView.setText(content);
                    contentView.setTextSize(16f);
                    contentView.setTextColor(Color.BLACK);
                    contentView.setPadding(0, 0, 0, 2);

                    // 긴 내용일 경우 한 줄만 표시 + ... 처리
                    contentView.setSingleLine(true);
                    contentView.setEllipsize(TextUtils.TruncateAt.END);

                    // (4) scheduleItemLayout에 순서대로 추가
                    scheduleItemLayout.addView(blueBox);
                    scheduleItemLayout.addView(contentView);

                    // (5) schedule_content_area에 추가
                    scheduleContentArea.addView(scheduleItemLayout);

                    count++;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // ...
            }
        });
    }



    private String createValidDateKey(int year, int month, int day) {
        return String.format("%d_%02d_%02d", year, month, day);
    }

    private void loadJobDataFromFirebase() {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("JOB");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                jobList.clear();
                Date now = new Date();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Job job = snapshot.getValue(Job.class);
                    if (job != null) {
                        try {
                            Date endDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(job.getEndDate());
                            if (endDate != null && endDate.after(now)) {
                                jobList.add(job);
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (jobList.size() > 4) {
                    jobList = jobList.subList(0, 4);
                }

                ViewPager2 recruitViewPager = findViewById(R.id.recruit_view_pager);
                TextView noJobsText = findViewById(R.id.no_jobs_text);

                if (jobList.isEmpty()) {
                    recruitViewPager.setVisibility(View.GONE);
                    noJobsText.setVisibility(View.VISIBLE);
                } else {
                    recruitViewPager.setVisibility(View.VISIBLE);
                    noJobsText.setVisibility(View.GONE);
                }

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
