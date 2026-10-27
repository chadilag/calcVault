package com.example.ui.vault

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultFileEntity
import com.example.ui.theme.CalcBlueAccentText
import com.example.ui.theme.CalcBluePrimary
import com.example.ui.theme.CalcVaultBorder
import com.example.ui.theme.CalcVaultCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

data class MountainPalette(
    val skyColor: Color,
    val horizonColor: Color,
    val mountainColor: Color,
    val sunColor: Color
)

val MountainPalettes = listOf(
    // 0: Blue & Gold (Photo viewer / Travel)
    MountainPalette(
        skyColor = Color(0xFF2E5180),
        horizonColor = Color(0xFF1C3252),
        mountainColor = Color(0xFF4772AC),
        sunColor = Color(0xFFF9D978)
    ),
    // 1: Teal & Amber (Family)
    MountainPalette(
        skyColor = Color(0xFF1F515C),
        horizonColor = Color(0xFF13343D),
        mountainColor = Color(0xFF337C88),
        sunColor = Color(0xFFF6C25B)
    ),
    // 2: Plum & Coral (Screenshots)
    MountainPalette(
        skyColor = Color(0xFF412854),
        horizonColor = Color(0xFF2A1839),
        mountainColor = Color(0xFF613E7C),
        sunColor = Color(0xFFF29076)
    ),
    // 3: Violet & Ice Moon (Private / Recently Added)
    MountainPalette(
        skyColor = Color(0xFF3A3154),
        horizonColor = Color(0xFF241E38),
        mountainColor = Color(0xFF5A4C80),
        sunColor = Color(0xFF9AC5FF)
    ),
    // 4: Slate & Warm Sun (Documents)
    MountainPalette(
        skyColor = Color(0xFF2B465E),
        horizonColor = Color(0xFF1B2E40),
        mountainColor = Color(0xFF476D8C),
        sunColor = Color(0xFFF4B166)
    ),
    // 5: Deep Violet & Mint Moon (Receipts)
    MountainPalette(
        skyColor = Color(0xFF3A2D4C),
        horizonColor = Color(0xFF241B30),
        mountainColor = Color(0xFF594570),
        sunColor = Color(0xFF7BE3C3)
    )
)

@Composable
fun MountainArtworkThumbnail(
    styleIndex: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    isSelected: Boolean = false,
    showSelectionCircle: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    isLockedBadge: Boolean = false,
    durationBadge: String? = null,
    dimmed: Boolean = false,
    file: VaultFileEntity? = null,
    loadThumbnail: (suspend (VaultFileEntity) -> ImageBitmap?)? = null,
    decryptFileBytes: (suspend (VaultFileEntity) -> ByteArray)? = null,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null
) {
    val palette = MountainPalettes[kotlin.math.abs(styleIndex) % MountainPalettes.size]
    var customBitmap by remember(file?.id, file?.encryptedFilePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file?.id, file?.encryptedFilePath) {
        if (file != null) {
            if (loadThumbnail != null) {
                customBitmap = loadThumbnail(file)
            } else if (decryptFileBytes != null && file.category == "PHOTO") {
                val bytes = decryptFileBytes(file)
                if (bytes.isNotEmpty()) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        customBitmap = bmp.asImageBitmap()
                    }
                }
            }
        } else {
            customBitmap = null
        }
    }

    val boxMod = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .then(
            if (isSelected) {
                Modifier.border(2.5.dp, CalcBluePrimary, RoundedCornerShape(cornerRadius))
            } else {
                Modifier
            }
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Box(modifier = boxMod) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap!!,
                contentDescription = file?.originalName ?: "Vault media",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CalcBluePrimary.copy(alpha = 0.18f))
                )
            }
        } else if (file != null && (file.category == "DOCUMENT" || file.category == "AUDIO")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF17253E))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = file.originalName,
                        tint = CalcBlueAccentText,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = file.originalName,
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.originalSizeBytes),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val horizonY = h * 0.72f

                // Sky background
                drawRect(
                    color = palette.skyColor,
                    topLeft = Offset.Zero,
                    size = Size(w, horizonY)
                )

                // Lower horizon band
                drawRect(
                    color = palette.horizonColor,
                    topLeft = Offset(0f, horizonY),
                    size = Size(w, h - horizonY)
                )

                // Sun / Moon circle in upper right
                val sunRadius = minOf(w, h) * 0.115f
                drawCircle(
                    color = palette.sunColor,
                    radius = sunRadius,
                    center = Offset(w * 0.77f, h * 0.27f)
                )

                // Geometric Mountain Triangle Peak
                val mountainPath = Path().apply {
                    moveTo(w * 0.10f, horizonY)
                    lineTo(w * 0.37f, h * 0.36f)
                    lineTo(w * 0.67f, horizonY)
                    close()
                }
                drawPath(
                    path = mountainPath,
                    color = palette.mountainColor
                )

                if (dimmed) {
                    drawRect(color = Color.Black.copy(alpha = 0.45f))
                }
                if (isSelected) {
                    drawRect(color = CalcBluePrimary.copy(alpha = 0.18f))
                }
            }
        }

        // Video play overlay icon when item is a video
        if (file?.category == "VIDEO") {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xB30B101B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Video",
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Video duration or category pill in bottom-left
        val badgeText = durationBadge ?: if (file?.category == "VIDEO") "VIDEO" else null
        if (!badgeText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC0B101B))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    color = TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Lock badge in top-right (used on Private album card)
        if (isLockedBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0B101B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Locked album",
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Selection checkmark circle in top-right
        if (isSelected || showSelectionCircle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) CalcBluePrimary else Color(0x990B101B))
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) CalcBluePrimary else Color.White.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
                    .then(
                        if (onToggleSelect != null) {
                            Modifier.clickable { onToggleSelect() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF0B101B),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CircularHeaderButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CalcVaultCard)
            .border(1.dp, CalcVaultBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextWhite,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun CalcVaultSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = CalcBluePrimary,
            uncheckedThumbColor = Color(0xFF94A3B8),
            uncheckedTrackColor = Color(0xFF1E293B),
            uncheckedBorderColor = Color.Transparent
        ),
        modifier = modifier
    )
}
