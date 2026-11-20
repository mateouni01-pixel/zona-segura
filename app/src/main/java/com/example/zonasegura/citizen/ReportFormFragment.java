package com.example.zonasegura.citizen;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.example.zonasegura.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Locale;

public final class ReportFormFragment extends Fragment {
    private static final double[][] LOCATIONS = {{-17.783300, -63.182100}, {-17.782100, -63.183000}, {-17.784500, -63.181000}};
    private int locationIndex;
    private String photoUri = "";
    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            photoUri = uri.toString();
            refreshPhoto();
        } catch (SecurityException exception) {
            Toast.makeText(requireContext(), R.string.zs_photo_error, Toast.LENGTH_LONG).show();
        }
    });

    public ReportFormFragment() { super(R.layout.fragment_citizen_form); }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            locationIndex = state.getInt("location", 0);
            photoUri = state.getString("photo", "");
        }
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MaterialAutoCompleteTextView type = view.findViewById(R.id.formType);
        type.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line,
                new String[]{getString(R.string.zs_lighting), getString(R.string.zs_infrastructure)}));
        view.findViewById(R.id.formChangeLocation).setOnClickListener(button -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.zs_location_picker).setSingleChoiceItems(R.array.zs_demo_locations, locationIndex, (dialog, which) -> {
                    locationIndex = which; refreshLocation(); dialog.dismiss();
                }).setNegativeButton(R.string.zs_cancel, null).show());
        view.findViewById(R.id.formAttach).setOnClickListener(button -> photoPicker.launch(new String[]{"image/*"}));
        view.findViewById(R.id.formRemovePhoto).setOnClickListener(button -> { photoUri = ""; refreshPhoto(); });
        view.findViewById(R.id.formSave).setOnClickListener(button -> save());
        refreshLocation(); refreshPhoto();
    }

    private void refreshLocation() {
        if (getView() == null) return;
        ((android.widget.TextView) getView().findViewById(R.id.formLocationName)).setText(getResources().getStringArray(R.array.zs_demo_locations)[locationIndex]);
        ((android.widget.TextView) getView().findViewById(R.id.formCoordinates)).setText(String.format(Locale.US, "%.6f, %.6f", LOCATIONS[locationIndex][0], LOCATIONS[locationIndex][1]));
    }

    private void refreshPhoto() {
        if (getView() == null) return;
        ImageView image = getView().findViewById(R.id.formPhotoPreview);
        CitizenUi.showPhoto(image, photoUri);
        getView().findViewById(R.id.formRemovePhoto).setVisibility(photoUri.isEmpty() ? View.GONE : View.VISIBLE);
        if (!photoUri.isEmpty() && image.getVisibility() != View.VISIBLE)
            Toast.makeText(requireContext(), R.string.zs_photo_error, Toast.LENGTH_SHORT).show();
    }

    private void save() {
        View view = requireView();
        MaterialAutoCompleteTextView typeInput = view.findViewById(R.id.formType);
        TextInputEditText descriptionInput = view.findViewById(R.id.formDescription);
        String description = descriptionInput.getText() == null ? "" : descriptionInput.getText().toString().trim();
        int typeId = typeInput.getText().toString().equals(getString(R.string.zs_lighting)) ? 1
                : typeInput.getText().toString().equals(getString(R.string.zs_infrastructure)) ? 2 : 0;
        TextInputLayout typeLayout = view.findViewById(R.id.formTypeLayout);
        TextInputLayout descriptionLayout = view.findViewById(R.id.formDescriptionLayout);
        typeLayout.setError(typeId == 0 ? getString(R.string.zs_type_required) : null);
        descriptionLayout.setError(ReportRules.validDescription(description) ? null : getString(R.string.zs_description_invalid));
        if (typeId == 0 || !ReportRules.validDescription(description)) {
            if (typeId == 0) typeInput.requestFocus(); else descriptionInput.requestFocus();
            return;
        }
        CitizenReport report = new DemoReportRepository(requireContext()).create(typeId, description,
                LOCATIONS[locationIndex][0], LOCATIONS[locationIndex][1], photoUri);
        if (ViewCompat.getWindowInsetsController(view) != null)
            ViewCompat.getWindowInsetsController(view).hide(WindowInsetsCompat.Type.ime());
        typeInput.setText("", false); descriptionInput.setText(""); photoUri = ""; locationIndex = 0;
        refreshPhoto(); refreshLocation();
        startActivity(new Intent(requireContext(), ReportResultActivity.class).putExtra("reportId", report.id));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt("location", locationIndex); state.putString("photo", photoUri);
        super.onSaveInstanceState(state);
    }
}
