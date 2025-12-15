package com.blackbriar.musicsupervisor.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.blackbriar.musicsupervisor.data.local.entity.ItemEntity

@Composable
fun SearchScreen(viewModel: SearchViewModel, onItemClick: (ItemEntity) -> Unit) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val results by viewModel.results.collectAsState()

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.search(it.text)
            },
            label = { Text("Search by title or author") },
            modifier = Modifier.fillMaxWidth()
        )

        // Display results
        results.forEach { item ->
            Text(
                text = "${item.title} by ${item.author}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) }
                    .padding(8.dp)
            )
        }
    }
}
