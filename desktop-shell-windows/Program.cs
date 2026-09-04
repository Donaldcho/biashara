using System.Diagnostics;
using System.Net.Http;
using System.Text.RegularExpressions;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace BiasharaDesktopShell;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        using var form = new DesktopShellForm(args);
        Application.Run(form);
    }
}

internal sealed class DesktopShellForm : Form
{
    private const string MainClass = "com.biasharaai.desktop.v2.BiasharaDesktopWebApp";
    private const string BackendJar = "BiasharaAIDesktopStandalone.jar";
    private readonly string[] args;
    private readonly Panel loadingPanel = new();
    private readonly Label titleLabel = new();
    private readonly Label statusLabel = new();
    private readonly ProgressBar progress = new();
    private readonly WebView2 webView = new();
    private Process? backendProcess;
    private string backendUrl = "";

    internal DesktopShellForm(string[] args)
    {
        this.args = args;
        Text = "Biashara AI Pro Desktop";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(1160, 760);
        Width = 1440;
        Height = 920;
        BackColor = Color.FromArgb(245, 247, 251);
        Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);

        webView.Dock = DockStyle.Fill;
        webView.Visible = false;
        Controls.Add(webView);

        BuildLoadingPanel();
        Controls.Add(loadingPanel);
    }

    protected override async void OnShown(EventArgs e)
    {
        base.OnShown(e);
        await StartBackendAndUiAsync();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        base.OnFormClosing(e);
        try
        {
            if (backendProcess is { HasExited: false })
            {
                backendProcess.Kill(entireProcessTree: true);
                backendProcess.Dispose();
            }
        }
        catch
        {
            // The process may already be exiting while Windows closes the app.
        }
    }

    private void BuildLoadingPanel()
    {
        loadingPanel.Dock = DockStyle.Fill;
        loadingPanel.BackColor = Color.FromArgb(245, 247, 251);

        var card = new Panel
        {
            Width = 560,
            Height = 260,
            BackColor = Color.White,
            Anchor = AnchorStyles.None
        };
        card.Paint += (_, paint) =>
        {
            using var border = new Pen(Color.FromArgb(216, 224, 235), 1);
            paint.Graphics.DrawRectangle(border, 0, 0, card.Width - 1, card.Height - 1);
        };

        var mark = new Label
        {
            Text = "B",
            Width = 54,
            Height = 54,
            TextAlign = ContentAlignment.MiddleCenter,
            ForeColor = Color.White,
            BackColor = Color.FromArgb(37, 99, 235),
            Font = new Font("Segoe UI", 24F, FontStyle.Bold, GraphicsUnit.Point),
            Location = new Point(34, 32)
        };

        titleLabel.Text = "Biashara AI Pro Desktop";
        titleLabel.AutoSize = true;
        titleLabel.ForeColor = Color.FromArgb(17, 24, 39);
        titleLabel.Font = new Font("Segoe UI", 18F, FontStyle.Bold, GraphicsUnit.Point);
        titleLabel.Location = new Point(106, 34);

        statusLabel.Text = "Starting offline workstation...";
        statusLabel.AutoSize = false;
        statusLabel.Width = 480;
        statusLabel.Height = 48;
        statusLabel.ForeColor = Color.FromArgb(66, 82, 106);
        statusLabel.Font = new Font("Segoe UI", 10.5F, FontStyle.Regular, GraphicsUnit.Point);
        statusLabel.Location = new Point(36, 108);

        progress.Style = ProgressBarStyle.Marquee;
        progress.MarqueeAnimationSpeed = 24;
        progress.Width = 488;
        progress.Height = 12;
        progress.Location = new Point(36, 172);

        var foot = new Label
        {
            Text = "Local data stays on this computer. Phone sync starts only through the desktop bridge.",
            AutoSize = false,
            Width = 488,
            Height = 42,
            ForeColor = Color.FromArgb(95, 111, 133),
            Font = new Font("Segoe UI", 9.5F, FontStyle.Regular, GraphicsUnit.Point),
            Location = new Point(36, 202)
        };

        card.Controls.Add(mark);
        card.Controls.Add(titleLabel);
        card.Controls.Add(statusLabel);
        card.Controls.Add(progress);
        card.Controls.Add(foot);
        loadingPanel.Controls.Add(card);

        void PositionCard()
        {
            card.Left = (loadingPanel.Width - card.Width) / 2;
            card.Top = (loadingPanel.Height - card.Height) / 2;
        }

        loadingPanel.Resize += (_, _) => PositionCard();
        PositionCard();
    }

    private async Task StartBackendAndUiAsync()
    {
        try
        {
            statusLabel.Text = "Finding desktop backend...";
            var jar = LocateBackendJar();
            var java = LocateJavaExecutable();

            statusLabel.Text = "Starting local data engine...";
            backendUrl = await StartBackendAsync(java, jar);

            statusLabel.Text = "Opening secure desktop window...";
            await StartWebViewAsync(backendUrl);

            loadingPanel.Visible = false;
            webView.Visible = true;
        }
        catch (Exception ex)
        {
            ShowFailure(ex.Message);
        }
    }

    private async Task<string> StartBackendAsync(string java, string jar)
    {
        var backendArgs = new List<string>
        {
            "-cp",
            Quote(jar),
            MainClass,
            "--no-open"
        };
        if (args.Contains("--no-phone", StringComparer.OrdinalIgnoreCase))
        {
            backendArgs.Add("--no-phone");
        }

        var startInfo = new ProcessStartInfo
        {
            FileName = java,
            Arguments = string.Join(" ", backendArgs),
            WorkingDirectory = Path.GetDirectoryName(jar) ?? AppContext.BaseDirectory,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        };

        backendProcess = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
        var outputTask = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        var errors = new List<string>();

        backendProcess.OutputDataReceived += (_, eventArgs) =>
        {
            if (string.IsNullOrWhiteSpace(eventArgs.Data))
            {
                return;
            }
            var match = Regex.Match(eventArgs.Data, @"http://127\.0\.0\.1:\d+/?");
            if (match.Success)
            {
                outputTask.TrySetResult(match.Value.TrimEnd('/'));
            }
        };
        backendProcess.ErrorDataReceived += (_, eventArgs) =>
        {
            if (!string.IsNullOrWhiteSpace(eventArgs.Data))
            {
                errors.Add(eventArgs.Data);
            }
        };
        backendProcess.Exited += (_, _) =>
        {
            outputTask.TrySetException(new InvalidOperationException("Desktop backend exited before the UI opened."));
        };

        if (!backendProcess.Start())
        {
            throw new InvalidOperationException("Could not start Java backend process.");
        }

        backendProcess.BeginOutputReadLine();
        backendProcess.BeginErrorReadLine();

        var completed = await Task.WhenAny(outputTask.Task, Task.Delay(TimeSpan.FromSeconds(18)));
        if (completed != outputTask.Task)
        {
            var errorText = errors.Count == 0 ? "" : " " + string.Join(" ", errors.TakeLast(3));
            throw new TimeoutException("Desktop backend did not report a local URL." + errorText);
        }

        var url = await outputTask.Task;
        await WaitForHealthAsync(url);
        return url;
    }

    private static async Task WaitForHealthAsync(string url)
    {
        using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(2) };
        for (var i = 0; i < 30; i++)
        {
            try
            {
                using var response = await client.GetAsync(url + "/api/state");
                if (response.IsSuccessStatusCode)
                {
                    return;
                }
            }
            catch
            {
                await Task.Delay(250);
            }
        }

        throw new TimeoutException("Desktop backend started but did not answer local API checks.");
    }

    private async Task StartWebViewAsync(string url)
    {
        var userData = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Biashara AI Pro Desktop",
            "WebView2"
        );
        Directory.CreateDirectory(userData);

        var environment = await CoreWebView2Environment.CreateAsync(null, userData);
        await webView.EnsureCoreWebView2Async(environment);

        webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
        webView.CoreWebView2.Settings.AreDevToolsEnabled = false;
        webView.CoreWebView2.Settings.AreBrowserAcceleratorKeysEnabled = true;
        webView.CoreWebView2.NewWindowRequested += (_, eventArgs) =>
        {
            eventArgs.Handled = true;
            if (Uri.TryCreate(eventArgs.Uri, UriKind.Absolute, out var uri))
            {
                Process.Start(new ProcessStartInfo(uri.ToString()) { UseShellExecute = true });
            }
        };
        webView.CoreWebView2.Navigate(url);
    }

    private void ShowFailure(string message)
    {
        progress.Visible = false;
        statusLabel.Text = message;
        statusLabel.ForeColor = Color.FromArgb(185, 28, 28);
    }

    private static string LocateBackendJar()
    {
        var baseDir = AppContext.BaseDirectory;
        var candidates = new[]
        {
            Path.Combine(baseDir, "lib", BackendJar),
            Path.Combine(baseDir, "..", "lib", BackendJar),
            Path.Combine(baseDir, "biasharaai-desktop", "lib", BackendJar),
            Path.Combine(baseDir, "..", "biasharaai-desktop", "lib", BackendJar),
            Path.Combine(Directory.GetCurrentDirectory(), "lib", BackendJar),
            Path.Combine(Directory.GetCurrentDirectory(), "..", "lib", BackendJar),
            Path.Combine(Directory.GetCurrentDirectory(), "desktop-standalone", "build", "libs", BackendJar),
            Path.Combine(Directory.GetCurrentDirectory(), "..", "desktop-standalone", "build", "libs", BackendJar)
        };

        foreach (var candidate in candidates.Select(Path.GetFullPath))
        {
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }

        throw new FileNotFoundException("Could not find " + BackendJar + " beside the desktop shell.");
    }

    private static string LocateJavaExecutable()
    {
        var baseDir = AppContext.BaseDirectory;
        var candidates = new List<string>
        {
            Path.Combine(baseDir, "runtime", "bin", "java.exe")
        };

        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            candidates.Add(Path.Combine(javaHome, "bin", "java.exe"));
        }

        candidates.AddRange((Environment.GetEnvironmentVariable("PATH") ?? "")
            .Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries)
            .Select(path => Path.Combine(path, "java.exe")));

        foreach (var candidate in candidates)
        {
            try
            {
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }
            catch
            {
                // Ignore malformed PATH entries.
            }
        }

        return "java.exe";
    }

    private static string Quote(string value)
    {
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }
}
