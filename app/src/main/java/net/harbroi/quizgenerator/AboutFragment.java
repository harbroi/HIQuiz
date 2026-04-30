package net.harbroi.quizgenerator;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.pm.PackageInfoCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AboutFragment extends Fragment {

	private static final String CHANGELOG_ASSET_FILE = "changelog_updates.txt";

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
							 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_about_tab, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		TextView tvAboutVersion = view.findViewById(R.id.tvAboutVersion);
		TextView tvAboutLatestUpdates = view.findViewById(R.id.tvAboutLatestUpdates);
		tvAboutVersion.setText(getString(R.string.about_version, getCurrentVersionToken()));
		renderAboutLatestUpdates(tvAboutLatestUpdates);
	}

	private void renderAboutLatestUpdates(TextView tvAboutLatestUpdates) {
		List<String> changelogItems = readChangelogItems();
		if (changelogItems.isEmpty()) {
			tvAboutLatestUpdates.setText(R.string.changelog_empty);
			return;
		}

		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < changelogItems.size(); index++) {
			builder.append("\u2022 ").append(changelogItems.get(index));
			if (index < changelogItems.size() - 1) {
				builder.append("\n");
			}
		}
		tvAboutLatestUpdates.setText(builder.toString());
	}

	private List<String> readChangelogItems() {
		List<String> items = new ArrayList<>();
		try (InputStream inputStream = requireContext().getAssets().open(CHANGELOG_ASSET_FILE);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					items.add(trimmed);
				}
			}
		} catch (IOException exception) {
			items.clear();
		}
		return items;
	}

	private String getCurrentVersionToken() {
		try {
			PackageInfo packageInfo = requireContext().getPackageManager()
					.getPackageInfo(requireContext().getPackageName(), 0);
			String versionName = packageInfo.versionName == null ? "" : packageInfo.versionName;
			long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
			return versionName + " (" + versionCode + ")";
		} catch (PackageManager.NameNotFoundException exception) {
			return "unknown";
		}
	}
}
