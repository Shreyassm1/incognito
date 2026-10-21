package com.incognito.vault.desktop.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.incognito.vault.crypto.MediaType
import com.incognito.vault.desktop.service.DesktopVaultService
import com.incognito.vault.desktop.service.GalleryFilter
import com.incognito.vault.desktop.service.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryFilterBar(
    service: DesktopVaultService
) {
    val totalCount = service.mediaItems.size
    val photoCount = service.mediaItems.count { it.mediaType == MediaType.PHOTO }
    val videoCount = service.mediaItems.count { it.mediaType == MediaType.VIDEO }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Filter Tabs / Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = service.currentFilter == GalleryFilter.ALL,
                    onClick = { service.currentFilter = GalleryFilter.ALL },
                    label = { Text("All Media ($totalCount)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                FilterChip(
                    selected = service.currentFilter == GalleryFilter.PHOTOS,
                    onClick = { service.currentFilter = GalleryFilter.PHOTOS },
                    label = { Text("Photos ($photoCount)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                FilterChip(
                    selected = service.currentFilter == GalleryFilter.VIDEOS,
                    onClick = { service.currentFilter = GalleryFilter.VIDEOS },
                    label = { Text("Videos ($videoCount)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Search and Sort
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Field
                OutlinedTextField(
                    value = service.searchQuery,
                    onValueChange = { service.searchQuery = it },
                    placeholder = { Text("Search by ID or date...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (service.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { service.searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.width(220.dp).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // Sort Dropdown
                Box {
                    OutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (service.currentSortOrder) {
                                SortOrder.NEWEST_FIRST -> "Newest"
                                SortOrder.OLDEST_FIRST -> "Oldest"
                                SortOrder.LARGEST_FIRST -> "Size ↓"
                                SortOrder.SMALLEST_FIRST -> "Size ↑"
                            },
                            fontSize = 13.sp
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest First") },
                            onClick = {
                                service.currentSortOrder = SortOrder.NEWEST_FIRST
                                sortMenuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest First") },
                            onClick = {
                                service.currentSortOrder = SortOrder.OLDEST_FIRST
                                sortMenuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Largest First)") },
                            onClick = {
                                service.currentSortOrder = SortOrder.LARGEST_FIRST
                                sortMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Smallest First)") },
                            onClick = {
                                service.currentSortOrder = SortOrder.SMALLEST_FIRST
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
