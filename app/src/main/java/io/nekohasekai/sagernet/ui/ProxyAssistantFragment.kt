package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.nekohasekai.sagernet.PROXY_ASSISTANT_PORT
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.databinding.LayoutProxyAssistantBinding
import io.nekohasekai.sagernet.ktx.app

class ProxyAssistantFragment : NamedFragment(R.layout.layout_proxy_assistant) {

    private var binding: LayoutProxyAssistantBinding? = null
    private var proxyAddress: String? = null
    private var proxyPort = PROXY_ASSISTANT_PORT
    private var proxyMessage: String? = null

    private val connection = SagerConnection(
        SagerConnection.CONNECTION_ID_SHORTCUT
    )

    private val serviceCallback = object : SagerConnection.Callback {

        override fun stateChanged(
            state: BaseService.State,
            profileName: String?,
            msg: String?
        ) {
        }

        override fun onServiceConnected(service: ISagerNetService) {
        }

        override fun proxyAssistantChanged(
            active: Boolean,
            address: String?,
            port: Int,
            message: String?
        ) {
            proxyAddress = if (active) address else null
            proxyPort = if (port > 0) port else PROXY_ASSISTANT_PORT
            proxyMessage = message
            updateProxyAssistantUi()
        }
    }

    override fun name0() = app.getString(R.string.proxy_assistant)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentBinding = LayoutProxyAssistantBinding.bind(view)
        binding = currentBinding

        currentBinding.proxyServerPort.text = proxyPort.toString()
        currentBinding.proxyServerAddress.text =
            getString(R.string.proxy_assistant_not_ready)
        currentBinding.proxyInterfaceInfo.text =
            "Menunggu status Proxy Assistant..."

        currentBinding.proxyCopyAddress.setOnClickListener {
            val address = proxyAddress
            if (address != null) {
                copyText("DonghuaBox Proxy Address", address)
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.proxy_assistant_not_ready,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        currentBinding.proxyCopyPort.setOnClickListener {
            copyText("DonghuaBox Proxy Port", proxyPort.toString())
        }

        currentBinding.proxyGuideAndroid.setOnClickListener {
            showGuide(
                R.string.proxy_guide_android,
                "Open Wi-Fi settings and select the connected hotspot network. " +
                    "Choose Modify network or the pencil icon, then open Advanced options. " +
                    "Set Proxy to Manual, enter the server address and proxy port shown here, " +
                    "then save the settings."
            )
        }

        currentBinding.proxyGuideIos.setOnClickListener {
            showGuide(
                R.string.proxy_guide_ios,
                "Open Settings > Wi-Fi. Tap the information button (ⓘ) beside the connected " +
                    "hotspot. Open Configure Proxy, select Manual, enter the server address " +
                    "and proxy port shown here, then save."
            )
        }

        currentBinding.proxyGuideWindows.setOnClickListener {
            showGuide(
                R.string.proxy_guide_windows,
                "Open Settings > Network & Internet > Proxy. Under Manual proxy setup, " +
                    "select Edit or Set up. Enable the proxy, enter the server address and " +
                    "proxy port shown here, then save."
            )
        }

        updateProxyAssistantUi()
    }

    override fun onStart() {
        super.onStart()
        connection.connect(requireContext(), serviceCallback)
    }

    override fun onStop() {
        connection.disconnect(requireContext())
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun updateProxyAssistantUi() {
        val currentBinding = binding ?: return
        val address = proxyAddress

        currentBinding.proxyServerAddress.text =
            address ?: getString(R.string.proxy_assistant_not_ready)
        currentBinding.proxyServerPort.text = proxyPort.toString()
        currentBinding.proxyCopyAddress.isEnabled = address != null
        currentBinding.proxyInterfaceInfo.text =
            proxyMessage?.takeIf { it.isNotBlank() }
                ?: if (address != null) {
                    "Proxy relay aktif"
                } else {
                    "Relay belum aktif. Pastikan VPN dan hotspot menyala."
                }
    }

    private fun copyText(label: String, value: String) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(
            requireContext(),
            R.string.copy_success,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showGuide(title: Int, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
