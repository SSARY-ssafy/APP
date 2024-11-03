package com.example.ssary;

import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.os.Environment;


import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

public class MyExistTextActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST = 1; // 파일 선택 코드

    private TextView categoryTextView;
    private EditText postTitleEditText;
    private EditText postContentEditText;
    private TextView uploadedFileTextView;
    private LinearLayout uploadedFileContainer;
    private Button updatePostButton;
    private Button deletePostButton;
    private Button uploadFileButton;
    private Button changeFileButton;
    private Button deleteFileButton;
    private Button downloadFileButton;
    private Button savePostButton;


    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String fileName; // 파일 이름
    private String documentId;
    private String fileUrl; // 파일 URL (스토리지에서 가져옴)
    private Uri fileUri;    // 파일 URI

    private boolean isFileDeleted = false; // 파일 삭제 상태 추적

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_exist_text);

        // Firestore 및 Storage 초기화
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // 레이아웃 요소 초기화
        categoryTextView = findViewById(R.id.categoryTextView);
        postTitleEditText = findViewById(R.id.postTitleEditText);
        postContentEditText = findViewById(R.id.postContentEditText);

        updatePostButton = findViewById(R.id.updatePostButton);
        deletePostButton = findViewById(R.id.deletePostButton);

        uploadFileButton = findViewById(R.id.uploadFileButton);
        changeFileButton = findViewById(R.id.changeFileButton);
        deleteFileButton = findViewById(R.id.deleteFileButton);
        downloadFileButton = findViewById(R.id.downloadFileButton);
        savePostButton = findViewById(R.id.savePostButton);

        uploadedFileTextView = findViewById(R.id.uploadedFileTextView);
        uploadedFileTextView.setPaintFlags(uploadedFileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        uploadedFileContainer = findViewById(R.id.uploadedFileContainer);

        enableEditing(false);

        // Intent에서 데이터 받기
        Intent intent = getIntent();
        String category = intent.getStringExtra("category");
        String title = intent.getStringExtra("title");
        documentId = intent.getStringExtra("documentId");

        // 카테고리 설정
        categoryTextView.setText("카테고리: " + category);

        // Firestore에서 글 정보 로드
        loadPostFromFirestore(documentId);

        // ============== 리스너 =================

//        updatePostButton.setOnClickListener(v -> updatePost());
        savePostButton.setOnClickListener(v -> updatePost());
        updatePostButton.setOnClickListener(v -> enableEditing(true));

        deletePostButton.setOnClickListener(v -> deletePost());
        uploadedFileTextView.setOnClickListener(v -> openFile());

        uploadFileButton.setOnClickListener(v -> selectFile());
        changeFileButton.setOnClickListener(v -> selectFile());  // 바로 새 파일 선택
        deleteFileButton.setOnClickListener(v -> {
            isFileDeleted = true; // 파일 삭제 상태로 설정
            uploadedFileTextView.setText("업로드된 파일이 없습니다.");
            uploadedFileContainer.setVisibility(View.GONE);
        });
        downloadFileButton.setOnClickListener(v -> downloadFile());
    }

    private void enableEditing(boolean isEditable) {
        postTitleEditText.setEnabled(isEditable);
        postContentEditText.setEnabled(isEditable);

        if (isEditable) {
            uploadFileButton.setVisibility(View.VISIBLE);
            downloadFileButton.setVisibility(View.GONE);
            changeFileButton.setVisibility(View.VISIBLE);
            deleteFileButton.setVisibility(View.VISIBLE);
            savePostButton.setVisibility(View.VISIBLE);
            updatePostButton.setVisibility(View.GONE);
        } else {
            downloadFileButton.setVisibility(View.VISIBLE);
            changeFileButton.setVisibility(View.GONE);
            deleteFileButton.setVisibility(View.GONE);
            savePostButton.setVisibility(View.GONE);
            updatePostButton.setVisibility(View.VISIBLE);
        }
    }

    private void downloadFile() {
        if (fileUrl != null && !fileUrl.isEmpty()) {
            Uri fileUri = Uri.parse(fileUrl);
            String fileName = uploadedFileTextView.getText().toString();

            // DownloadManager를 사용하여 파일 다운로드
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(fileUri);
            request.setTitle(fileName);
            request.setDescription("파일 다운로드 중...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            // 다운로드 요청 실행
            downloadManager.enqueue(request);

            Toast.makeText(this, "파일 다운로드를 시작합니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "다운로드할 파일이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    private void loadPostFromFirestore(String documentId) {
        db.collection("posts").document(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Firestore에서 글 제목, 내용, 파일 정보 로드
                        String title = documentSnapshot.getString("title");
                        String content = documentSnapshot.getString("content");
                        fileUrl = documentSnapshot.getString("fileUrl");

                        postTitleEditText.setText(title);
                        postContentEditText.setText(content);

                        // 파일이 있는 경우 파일 이름을 표시하고 컨테이너 보이기
                        if (fileUrl != null && !fileUrl.isEmpty()) {
                            String fileName = documentSnapshot.getString("fileName");
                            uploadedFileTextView.setText(fileName != null ? fileName : "파일 이름 없음");
                            uploadedFileContainer.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(this, "글 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글을 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void openFile() {
        if (fileUrl != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(fileUrl), "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } else {
            Toast.makeText(this, "업로드된 파일이 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePost() {
        String updatedTitle = postTitleEditText.getText().toString().trim();
        String updatedContent = postContentEditText.getText().toString().trim();

        if (updatedTitle.isEmpty() || updatedContent.isEmpty()) {
            Toast.makeText(this, "제목과 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isFileDeleted && fileUrl != null) {
            // Storage에서 파일 삭제 후 Firestore 업데이트
            StorageReference fileRef = storage.getReferenceFromUrl(fileUrl);
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> {
                        fileUrl = null; // 파일 URL을 null로 설정하여 Firestore에서 제거
                        fileName = null; // 파일 이름도 null로 설정
                        saveUpdatedPost(updatedTitle, updatedContent, null, null);
                        Toast.makeText(this, "파일이 성공적으로 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        } else if (fileUri != null) {
            // 새로운 파일이 선택된 경우 기존 파일 삭제 후 새 파일 업로드
            if (fileUrl != null && !fileUrl.isEmpty()) {
                StorageReference oldFileRef = storage.getReferenceFromUrl(fileUrl);
                oldFileRef.delete().addOnSuccessListener(aVoid -> {
                    uploadNewFileAndSavePost(updatedTitle, updatedContent); // 기존 파일 삭제 후 새 파일 업로드
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "기존 파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
            } else {
                uploadNewFileAndSavePost(updatedTitle, updatedContent); // 기존 파일이 없을 경우 새 파일 업로드만 수행
            }
        } else {
            // 파일이 수정되지 않은 경우 Firestore에 제목과 내용만 업데이트
            saveUpdatedPost(updatedTitle, updatedContent, fileName, fileUrl);
        }
    }


    private void saveUpdatedPost(String title, String content, String fileName, String fileUrl) {
        db.collection("posts").document(documentId)
                .update("title", title, "content", content, "fileName", fileName, "fileUrl", fileUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    finish(); // 수정 후 액티비티 종료
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadNewFileAndSavePost(String title, String content) {
        fileName = fileUri.getLastPathSegment();
        String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
        StorageReference newFileRef = storage.getReference().child("uploads/" + uniqueFileName);

        newFileRef.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> newFileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String newFileUrl = uri.toString();

                    // Firestore에 새로운 파일 정보와 함께 업데이트
                    db.collection("posts").document(documentId)
                            .update("title", title, "content", content, "fileName", fileName, "fileUrl", newFileUrl)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "글과 파일이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                                finish(); // 수정 후 액티비티 종료
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "글 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            });
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "새 파일 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }


    private void deletePost() {
        // Firebase Storage에서 파일 삭제
        if (fileUrl != null && !fileUrl.isEmpty()) {
            StorageReference fileRef = storage.getReferenceFromUrl(fileUrl);
            fileRef.delete()
                    .addOnSuccessListener(aVoid -> deletePostFromFirestore())
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            deletePostFromFirestore();
        }
    }

    private void deletePostFromFirestore() {
        // Firestore에서 글 삭제
        db.collection("posts").document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "글이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    finish(); // 삭제 후 액티비티 종료
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "글 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            fileUri = data.getData();
            fileName = fileUri.getLastPathSegment();
            uploadedFileTextView.setText(fileName != null ? fileName : "파일 이름을 불러올 수 없음");
            uploadedFileContainer.setVisibility(View.VISIBLE);
        }
    }
}
