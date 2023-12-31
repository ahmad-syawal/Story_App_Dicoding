package com.syawal.storyapp.ui.addstory

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.syawal.storyapp.databinding.FragmentAddStoryBinding
import android.Manifest
import android.content.Intent
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.LocationServices
import com.syawal.storyapp.R
import com.syawal.storyapp.data.ResultState
import com.syawal.storyapp.ui.viewmodelfactory.StoryViewModelFactory
import com.syawal.storyapp.ui.addstory.camerax.CameraActivity
import com.syawal.storyapp.ui.addstory.camerax.CameraActivity.Companion.CAMERAX_RESULT
import com.syawal.storyapp.utils.reduceFileImage
import com.syawal.storyapp.utils.uriToFile

class AddStoryFragment : Fragment() {

    private var _binding: FragmentAddStoryBinding? = null
    private val binding get() = _binding!!
    private var currentImageUri: Uri? = null
    private val addStoryViewModel by viewModels<AddStoryViewModel> {
        StoryViewModelFactory.getInstance(requireActivity())
    }

    private fun cameraPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            requireContext(),
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                showToast("Permission request granted")
            } else {
                showToast("Permission request denied")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddStoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!cameraPermissionsGranted()) {
            permissionLauncher.launch(CAMERA_PERMISSION)
        }

        binding.btnGallery.setOnClickListener {
            startGallery()
        }

        binding.btnCamera.setOnClickListener {
            startCameraX()
        }

        val checkbox = binding.checkbox
        binding.buttonAdd.setOnClickListener {
            if (checkbox.isChecked) {
                getMyLastLocation { lat, lon ->
                    upload(lat, lon)
                }
            } else {
                upload(lat = null, lon = null)
            }
        }
    }

    private fun startGallery() {
        launcherGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private val launcherGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            currentImageUri = uri
            showImage()
        } else {
            showToast(getString(R.string.failed_to_load_image))
        }
    }

    private fun showImage() {
        currentImageUri?.let {
            binding.previewImage.setImageURI(it)
        }
    }

    private fun upload(lat: Double?, lon: Double?) {
        currentImageUri?.let { uri ->
            val imgFile = uriToFile(uri, requireContext()).reduceFileImage()
            val desc = binding.edAddDescription.text.toString()

            addStoryViewModel.uploadStory(imgFile, desc, lat, lon)
                .observe(viewLifecycleOwner) { result ->
                    if (result != null) {
                        when (result) {
                            is ResultState.Loading -> {
                                showLoading(true)
                            }

                            is ResultState.Success -> {
                                showToast(result.data.message)
                                showLoading(false)
                                findNavController().navigate(R.id.action_addStoryFragment_to_homeFragment)
                            }

                            is ResultState.Error -> {
                                showToast(result.error)
                                showLoading(false)
                            }
                        }
                    }
                }
        }
    }

    private fun getMyLastLocation(callback: (Double?, Double?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                FINE_LOCATION_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(FINE_LOCATION_PERMISSION)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude

                callback(lat, lon)
            } else {
                showToast("Location not found")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun startCameraX() {
        val intent = Intent(requireContext(), CameraActivity::class.java)
        launcherIntentCameraX.launch(intent)
    }

    private val launcherIntentCameraX = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == CAMERAX_RESULT) {
            currentImageUri = it.data?.getStringExtra(CameraActivity.EXTRA_CAMERAX_IMAGE)?.toUri()
            showImage()
        }
    }

    companion object {
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val FINE_LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION
    }
}