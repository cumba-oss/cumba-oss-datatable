package net.cumba.datatable.io;

// OSS-IDENTITY datatable-probe-failures: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.Objects;
import javax.net.ssl.SSLException;
import org.jspecify.annotations.Nullable;

/**
 * The one rule for a {@code getProviderProperties} probe that reads its source and fails: a
 * <b>remote</b> source aborts the open, a local one keeps the provider's best-effort default.
 *
 * <p>
 * A probe reads the source before the properties dialog appears (the CSV provider's first line, the
 * {@code .dblib} JSON, the Define-XML). For a local file a failed read is harmless — the dialog
 * shows the static defaults and the load reports what is wrong. For an {@code http} / {@code https}
 * source the probe's read is the first network access: a rejected server certificate, a refused
 * connection or a 404 there means the load cannot succeed either, so showing the dialog would only
 * delay the error (and, for a rejected certificate, ask the user a second time). Such a failure is
 * therefore thrown, unchecked, from the probe.
 * </p>
 *
 * <p>
 * <b>Remote is decided by the URI scheme, never by the exception type</b>: a
 * {@link FileNotFoundException} is also what a missing local file throws. Only {@code http} and
 * {@code https} (ASCII case-insensitive) are remote; {@code file:} — a UNC path
 * {@code file://server/share/…} included —, {@code jar:} (even {@code jar:https:…}, which is scheme
 * {@code jar}), {@code databricks:} and a URI without a scheme are local here.
 * </p>
 *
 * <p>
 * The thrown {@link UncheckedIOException} carries the short headline the UI shows (<i>"Opening
 * aborted: host:port could not be read (reason)."</i>) and the original failure as its cause, so
 * the stack trace stays available. For a TLS failure the reason is the trust manager's own refusal
 * text — the innermost {@link CertificateException} in the chain — else the {@link SSLException}'s
 * message.
 * </p>
 *
 * <p>
 * Called only inside the catch clause of a probe's <b>read</b>, never around code that merely
 * interprets what was read.
 * </p>
 */
public final class ProbeFailures
{

    private static final int HTTP_DEFAULT_PORT = 80;

    private static final int HTTPS_DEFAULT_PORT = 443;

    private ProbeFailures()
    {
        // static helper
    }


    /**
     * Whether the given URI is read over the network: scheme {@code http} or {@code https}, ASCII
     * case-insensitive.
     *
     * @param aUri
     *            the URI; may be {@code null} (local).
     * @return {@code true} for an {@code http} / {@code https} URI.
     */
    public static boolean isRemote(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return false;
        }
        String scheme = aUri.getScheme();
        return scheme != null && (asciiEqualsIgnoreCase(scheme, "http")
                || asciiEqualsIgnoreCase(scheme, "https"));
    }


    /**
     * Throws the abort for a failed probe read of a remote source; returns for a local one, so the
     * caller keeps its default.
     *
     * @param aUri
     *            the source the probe read.
     * @param aFailure
     *            what the read threw.
     * @throws UncheckedIOException
     *             if {@code aUri} {@link #isRemote(URI) is remote}: its message is the headline
     *             <i>"Opening aborted: host:port could not be read (reason)."</i>, its cause the
     *             failure (a non-{@link IOException} wrapped in one).
     */
    public static void rethrowIfRemote(URI aUri, Throwable aFailure)
    {
        if (!isRemote(aUri))
        {
            return;
        }
        String reason = reasonOf(aFailure);
        IOException cause;
        if (aFailure instanceof IOException ioe)
        {
            cause = ioe;
        }
        else if (aFailure instanceof UncheckedIOException uioe)
        {
            cause = Objects.requireNonNullElse(uioe.getCause(), new IOException(reason, uioe));
        }
        else
        {
            cause = new IOException(reason, aFailure);
        }
        throw new UncheckedIOException(
                "Opening aborted: " + hostAndPort(aUri) + " could not be read (" + reason + ").",
                cause);
    }


    /**
     * The {@code host:port} of a remote URI, the scheme's default port filled in.
     *
     * @param aUri
     *            a remote URI.
     * @return {@code host:port}; the authority (or the whole URI) when there is no host.
     */
    static String hostAndPort(URI aUri)
    {
        String host = aUri.getHost();
        if (host == null)
        {
            String authority = aUri.getAuthority();
            return authority != null ? authority : aUri.toString();
        }
        int port = aUri.getPort();
        if (port < 0)
        {
            String scheme = aUri.getScheme();
            port = scheme != null && asciiEqualsIgnoreCase(scheme, "https") ? HTTPS_DEFAULT_PORT
                    : HTTP_DEFAULT_PORT;
        }
        return host + ":" + port;
    }


    /**
     * The short reason for the headline: the innermost {@link CertificateException}'s message (the
     * trust manager's refusal), else the first {@link SSLException}'s, else a plain wording of the
     * failure.
     *
     * @param aFailure
     *            what the read threw.
     * @return the reason, never blank.
     */
    static String reasonOf(Throwable aFailure)
    {
        CertificateException certificate = null;
        SSLException ssl = null;
        int depth = 0;
        for (Throwable t = aFailure; t != null && depth < 32; t = t.getCause(), depth++)
        {
            if (t instanceof CertificateException ce && !isBlank(ce.getMessage()))
            {
                certificate = ce;
            }
            else if (ssl == null && t instanceof SSLException se && !isBlank(se.getMessage()))
            {
                ssl = se;
            }
        }
        if (certificate != null)
        {
            return String.valueOf(certificate.getMessage());
        }
        if (ssl != null)
        {
            return String.valueOf(ssl.getMessage());
        }
        Throwable failure = aFailure instanceof UncheckedIOException uioe
                ? Objects.requireNonNullElse(uioe.getCause(), aFailure)
                : aFailure;
        if (failure instanceof FileNotFoundException)
        {
            // HttpURLConnection: 404 / 410, the message is just the URL
            return "not found";
        }
        if (failure instanceof UnknownHostException)
        {
            return "unknown host";
        }
        String message = failure.getMessage();
        return isBlank(message) ? failure.getClass().getSimpleName() : String.valueOf(message);
    }


    private static boolean isBlank(@Nullable String aText)
    {
        return aText == null || aText.isBlank();
    }


    private static boolean asciiEqualsIgnoreCase(String aText, String aLowerAscii)
    {
        if (aText.length() != aLowerAscii.length())
        {
            return false;
        }
        for (int i = 0; i < aText.length(); i++)
        {
            char c = aText.charAt(i);
            char lower = c >= 'A' && c <= 'Z' ? Character.toLowerCase(c) : c;
            if (lower != aLowerAscii.charAt(i))
            {
                return false;
            }
        }
        return true;
    }
}
