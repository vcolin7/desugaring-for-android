// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.vcolin7.desugaring;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.time.Duration;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

public class MainActivity extends AppCompatActivity {
    private final Disposable.Composite disposables = Disposables.composite();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onClick(View view) {
        TextView textView = findViewById(R.id.text_view);
        StringBuilder stringBuilder = new StringBuilder();
        //
        stringBuilder.append("APIVersion ->" + Build.VERSION.SDK_INT);
        stringBuilder.append("\n");
        stringBuilder.append("Mono.just(true).block() ->" + Mono.just(true).block());
        stringBuilder.append("\n");
        stringBuilder.append("java.time.ZonedDateTime.now() ->" + java.time.ZonedDateTime.now());
        textView.setText(stringBuilder.toString());
        //
        Disposable disposable = Mono.delay(Duration.ofSeconds(4))
                .map(i -> "Hello!")
                .publishOn(AndroidScheduler.mainThread())
                .doOnNext(str -> {
                    StringBuilder builder = new StringBuilder();
                    builder.append("Mono.delay(Duration.ofSeconds(4))")
                            .append("\n")
                            .append("    .map(i -> \"Hello!\")")
                            .append("\n")
                            .append("    .publishOn(AndroidSchedulers.mainThread())")
                            .append("\n")
                            .append("    .doOnNext(str -> {")
                            .append("\n")
                            .append("        textView.setText(str);")
                            .append("\n")
                            .append("    })")
                            .append("\n")
                            .append("    .subscribe();")
                            .append(" -> " + str);
                    textView.setText(builder.toString());
                })
                .subscribe();
        disposables.add(disposable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.dispose();
    }
}
