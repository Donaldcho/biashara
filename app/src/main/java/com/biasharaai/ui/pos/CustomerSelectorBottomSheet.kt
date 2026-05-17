package com.biasharaai.ui.pos

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.biasharaai.R
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.CustomerRepository
import com.biasharaai.databinding.FragmentCustomerSelectorBinding
import com.biasharaai.databinding.ItemCustomerRowBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Customer picker for POS: walk-in, new customer, searchable list — Prompt U2.
 * Invokes [onCustomerPicked] with `null` for walk-in.
 */
@AndroidEntryPoint
class CustomerSelectorBottomSheet : BottomSheetDialogFragment() {

    @Inject
    lateinit var customerRepository: CustomerRepository

    var onCustomerPicked: ((Customer?) -> Unit)? = null

    private var _binding: FragmentCustomerSelectorBinding? = null
    private val binding get() = _binding!!

    private var allCustomers: List<Customer> = emptyList()
    private var filterText: String = ""

    private lateinit var listAdapter: CustomerRowsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomerSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listAdapter = CustomerRowsAdapter()
        binding.recyclerCustomers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCustomers.adapter = listAdapter

        binding.editSearchCustomer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterText = s?.toString().orEmpty()
                listAdapter.submitRows(buildRows())
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customerRepository.observeAllCustomers().collect { customers ->
                    allCustomers = customers
                    listAdapter.submitRows(buildRows())
                }
            }
        }
    }

    private fun buildRows(): List<Row> {
        val rows = mutableListOf<Row>()
        rows.add(Row.WalkIn)
        rows.add(Row.NewCustomer)
        val q = filterText.trim().lowercase(Locale.getDefault())
        val filtered = if (q.isEmpty()) {
            allCustomers
        } else {
            allCustomers.filter { c ->
                c.name.lowercase(Locale.getDefault()).contains(q) ||
                    (c.phone?.lowercase(Locale.getDefault())?.contains(q) == true) ||
                    (c.email?.lowercase(Locale.getDefault())?.contains(q) == true)
            }
        }
        filtered.forEach { rows.add(Row.CustomerRow(it)) }
        return rows
    }

    private fun pick(customer: Customer?) {
        onCustomerPicked?.invoke(customer)
        dismiss()
    }

    private fun showNewCustomerDialog() {
        val ctx = requireContext()
        val pad = resources.getDimensionPixelSize(R.dimen.pos_dialog_padding)
        val nameInput = EditText(ctx).apply {
            hint = getString(R.string.pos_new_customer_name_hint)
        }
        val phoneInput = EditText(ctx).apply {
            hint = getString(R.string.pos_new_customer_phone_hint)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(nameInput)
            addView(phoneInput)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.pos_new_customer_title)
            .setView(wrap)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pos_new_customer_save) { _, _ ->
                val name = nameInput.text?.toString().orEmpty()
                val phone = phoneInput.text?.toString().orEmpty()
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        val id = customerRepository.insertCustomer(name, phone.ifBlank { null })
                        val c = customerRepository.getCustomerById(id)
                        if (c != null) pick(c)
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    sealed class Row {
        data object WalkIn : Row()
        data object NewCustomer : Row()
        data class CustomerRow(val customer: Customer) : Row()
    }

    private inner class CustomerRowsAdapter : RecyclerView.Adapter<VH>() {
        private val rows = mutableListOf<Row>()

        fun submitRows(newRows: List<Row>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.WalkIn -> VT_WALK_IN
            is Row.NewCustomer -> VT_NEW
            is Row.CustomerRow -> VT_CUSTOMER
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inf = LayoutInflater.from(parent.context)
            val b = ItemCustomerRowBinding.inflate(inf, parent, false)
            return when (viewType) {
                VT_WALK_IN -> WalkInVH(b)
                VT_NEW -> NewCustomerVH(b)
                else -> CustomerVH(b)
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            when (val row = rows[position]) {
                is Row.WalkIn -> (holder as WalkInVH).bind()
                is Row.NewCustomer -> (holder as NewCustomerVH).bind()
                is Row.CustomerRow -> (holder as CustomerVH).bind(row.customer)
            }
        }
    }

    abstract inner class VH(view: View) : RecyclerView.ViewHolder(view)

    inner class WalkInVH(
        private val rowBinding: ItemCustomerRowBinding,
    ) : VH(rowBinding.root) {
        fun bind() {
            rowBinding.textName.setText(R.string.pos_walk_in_customer)
            rowBinding.textSubtitle.setText(R.string.pos_walk_in_subtitle)
            rowBinding.root.setOnClickListener { pick(null) }
        }
    }

    inner class NewCustomerVH(
        private val rowBinding: ItemCustomerRowBinding,
    ) : VH(rowBinding.root) {
        fun bind() {
            rowBinding.textName.setText(R.string.pos_new_customer_row)
            rowBinding.textSubtitle.setText(R.string.pos_new_customer_row_subtitle)
            rowBinding.root.setOnClickListener { showNewCustomerDialog() }
        }
    }

    inner class CustomerVH(
        private val rowBinding: ItemCustomerRowBinding,
    ) : VH(rowBinding.root) {
        private val dateFormat: DateFormat =
            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())

        fun bind(customer: Customer) {
            rowBinding.textName.text = customer.name
            val visitPart = when {
                customer.lastVisit > 0L ->
                    getString(R.string.pos_customer_last_visit, dateFormat.format(Date(customer.lastVisit)))
                customer.createdAt > 0L ->
                    getString(R.string.pos_customer_since, dateFormat.format(Date(customer.createdAt)))
                else -> getString(R.string.pos_customer_never_visited)
            }
            val phonePart = customer.phone?.takeIf { it.isNotBlank() }
            rowBinding.textSubtitle.text = listOfNotNull(visitPart, phonePart).joinToString(" · ")
            rowBinding.root.setOnClickListener { pick(customer) }
        }
    }

    companion object {
        private const val VT_WALK_IN = 0
        private const val VT_NEW = 1
        private const val VT_CUSTOMER = 2

        fun newInstance(): CustomerSelectorBottomSheet = CustomerSelectorBottomSheet()
    }
}
