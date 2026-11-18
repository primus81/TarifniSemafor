<?php
/**
 * Tarifni semafor - daily price list job (cron).
 *
 * What it does, in this order:
 *  1. Reads the price data you maintain by hand (ceniki_vir.json), checks it with the same rules as the app and, if it
 *     is valid and different from the published file, publishes it atomically as public_html/api/ceniki.json with a new
 *     updatedAt. An invalid source file is NEVER published: the old, valid file stays online.
 *  2. Watches the official source pages listed in watch.json (supplier price lists, SODO, AGEN-RS, energetika-portal,
 *     FURS ...). When a page changes, you get an e-mail: then check the new price list and update ceniki_vir.json.
 *     Prices are never taken over automatically from a web page - the pages have no stable structure, and a wrong
 *     number shown as "the price" would be worse than an old one.
 *  3. Reminds you by e-mail some days before a price list in use expires.
 *
 * Run from the command line only:  php /path/to/private/update_prices.php
 * Nothing about app users is processed here: the script only reads and writes the price files.
 */

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(403);
    exit;
}

date_default_timezone_set('Europe/Ljubljana');

$configFile = __DIR__ . '/config.php';
$config = array_merge([
    'notify_email' => '',
    'mail_from' => '',
    'source_file' => __DIR__ . '/ceniki_vir.json',
    'public_file' => __DIR__ . '/../public_html/api/ceniki.json',
    'watch_file' => __DIR__ . '/watch.json',
    'state_file' => __DIR__ . '/state.json',
    'log_file' => __DIR__ . '/update_prices.log',
    'lead_days' => 21,
    'user_agent' => 'TarifniSemafor-cenik-monitor',
], is_file($configFile) ? (require $configFile) : []);

$report = [];   // lines for the e-mail
$problem = false;

// ---------------------------------------------------------------- validation (same rules as PriceDataParser.kt)

function is_date_string($v): bool
{
    if (!is_string($v) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $v)) return false;
    [$y, $m, $d] = array_map('intval', explode('-', $v));
    return checkdate($m, $d, $y);
}

function check_number($v, float $min, float $max, bool $minExclusive = false): bool
{
    if (!is_int($v) && !is_float($v)) return false;
    $f = (float)$v;
    if (!is_finite($f)) return false;
    return ($minExclusive ? $f > $min : $f >= $min) && $f <= $max;
}

function check_url($v): bool
{
    return is_string($v) && strpos($v, 'https://') === 0 && strlen($v) <= 500;
}

function check_period(array $o, string $where, array &$errors, bool $toRequired = false): void
{
    if (!is_date_string($o['validFrom'] ?? null)) $errors[] = "$where: validFrom";
    $to = $o['validTo'] ?? null;
    if ($to === null) {
        if ($toRequired) $errors[] = "$where: validTo";
    } elseif (!is_date_string($to) || (is_date_string($o['validFrom'] ?? null) && $to < $o['validFrom'])) {
        $errors[] = "$where: validTo";
    }
}

/** Returns a list of errors; empty = valid. */
function validate(array $d): array
{
    $e = [];
    if (($d['schemaVersion'] ?? null) !== 1) $e[] = 'schemaVersion must be 1';
    if (!is_string($d['updatedAt'] ?? null) || strtotime($d['updatedAt']) === false) $e[] = 'updatedAt';
    check_period($d, 'file', $e);

    $t = $d['tariffTimes'] ?? null;
    if (!is_array($t)) {
        $e[] = 'tariffTimes missing';
    } else {
        $s = $t['vtStartHour'] ?? null;
        $en = $t['vtEndHour'] ?? null;
        if (!is_int($s) || $s < 0 || $s > 23 || !is_int($en) || $en < 1 || $en > 24 || $s === $en % 24) $e[] = 'tariffTimes hours';
        if (!is_bool($t['mtOnWeekendsAndHolidays'] ?? null)) $e[] = 'tariffTimes.mtOnWeekendsAndHolidays';
        if (!check_url($t['sourceUrl'] ?? null)) $e[] = 'tariffTimes.sourceUrl';
    }

    $taxes = $d['taxes'] ?? null;
    if (!is_array($taxes)) {
        $e[] = 'taxes missing';
    } else {
        if (!check_number($taxes['vatRate'] ?? null, 0.0, 0.5, true)) $e[] = 'taxes.vatRate (0-0.5)';
        $units = ['EUR/kWh' => 1.0, 'EUR/kW/month' => 50.0, 'EUR/month' => 1000.0];
        foreach (['ureContribution', 'borzenContribution', 'exciseDuty', 'oveSpteContribution'] as $key) {
            $list = $taxes[$key] ?? null;
            if (!is_array($list) || count($list) === 0) {
                $e[] = "taxes.$key missing";
                continue;
            }
            foreach ($list as $i => $c) {
                $w = "taxes.$key[$i]";
                $unit = $c['unit'] ?? null;
                if (!isset($units[$unit])) {
                    $e[] = "$w.unit";
                    continue;
                }
                if (!check_number($c['value'] ?? null, 0.0, $units[$unit])) $e[] = "$w.value out of range";
                check_period($c, $w, $e);
                if (!check_url($c['sourceUrl'] ?? null)) $e[] = "$w.sourceUrl";
                if ($unit === 'EUR/kW/month') {
                    foreach (['powerBlockHighSeason', 'powerBlockLowSeason'] as $b) {
                        if (!is_int($c[$b] ?? null) || $c[$b] < 1 || $c[$b] > 5) $e[] = "$w.$b";
                    }
                }
            }
        }
    }

    $network = $d['network'] ?? null;
    if (!is_array($network)) {
        $e[] = 'network missing';
    } else {
        foreach ($network as $i => $n) {
            $w = "network[$i]";
            check_period($n, $w, $e, true);
            if (!check_url($n['sourceUrl'] ?? null)) $e[] = "$w.sourceUrl";
            $blocks = [];
            foreach (($n['blocks'] ?? []) as $r) {
                $blocks[] = $r['block'] ?? null;
                if (!check_number($r['powerEurPerKwMonth'] ?? null, 0.0, 50.0)) $e[] = "$w block power";
                if (!check_number($r['energyEurPerKwh'] ?? null, 0.0, 1.0)) $e[] = "$w block energy";
            }
            sort($blocks);
            if ($blocks !== [1, 2, 3, 4, 5]) $e[] = "$w must have blocks 1-5";
        }
    }

    $suppliers = $d['suppliers'] ?? null;
    if (!is_array($suppliers)) {
        $e[] = 'suppliers missing';
    } else {
        $ids = [];
        $pids = [];
        foreach ($suppliers as $i => $s) {
            $w = "suppliers[$i]";
            $id = $s['id'] ?? '';
            if (!is_string($id) || !preg_match('/^[a-z0-9][a-z0-9_-]{0,63}$/', $id) || isset($ids[$id])) $e[] = "$w.id";
            $ids[$id] = true;
            if (!is_string($s['name'] ?? null) || trim($s['name']) === '') $e[] = "$w.name";
            $packages = $s['packages'] ?? [];
            if (!is_array($packages) || count($packages) === 0) $e[] = "$w.packages";
            foreach ($packages as $j => $p) {
                $pw = "$w.packages[$j]";
                $pid = $p['id'] ?? '';
                if (!is_string($pid) || !preg_match('/^[a-z0-9][a-z0-9_-]{0,63}$/', $pid) || isset($pids[$pid])) $e[] = "$pw.id";
                $pids[$pid] = true;
                if (!is_string($p['name'] ?? null) || trim($p['name']) === '') $e[] = "$pw.name";
                check_period($p, $pw, $e);
                if (!check_url($p['sourceUrl'] ?? null)) $e[] = "$pw.sourceUrl";
                $pr = $p['pricesWithoutVat'] ?? [];
                foreach (['ET', 'VT', 'MT'] as $k) {
                    if (array_key_exists($k, $pr) && $pr[$k] !== null && !check_number($pr[$k], 0.0, 2.0, true)) $e[] = "$pw.$k out of range";
                }
                $hasEt = isset($pr['ET']);
                $hasVt = isset($pr['VT']);
                $hasMt = isset($pr['MT']);
                if ($hasVt !== $hasMt) $e[] = "$pw: VT and MT come together";
                if (!$hasEt && !($hasVt && $hasMt)) $e[] = "$pw: no prices";
                $fee = $p['monthlyFeeWithoutVat'] ?? null;
                if ($fee !== null && !check_number($fee, 0.0, 1000.0)) $e[] = "$pw.monthlyFeeWithoutVat";
            }
        }
    }
    return $e;
}

// ---------------------------------------------------------------- helpers

function load_json(string $file): ?array
{
    if (!is_file($file)) return null;
    $d = json_decode((string)file_get_contents($file), true);
    return is_array($d) ? $d : null;
}

/** Content hash without updatedAt (a new timestamp alone is not a change). */
function content_hash(array $d): string
{
    unset($d['updatedAt']);
    return hash('sha256', json_encode($d, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
}

function write_atomically(string $file, string $text): bool
{
    $tmp = $file . '.tmp';
    if (file_put_contents($tmp, $text, LOCK_EX) === false) return false;
    return rename($tmp, $file);
}

function log_line(array $config, string $line): void
{
    file_put_contents($config['log_file'], date('c') . ' ' . $line . "\n", FILE_APPEND | LOCK_EX);
}

/** The latest end date among the items in use today, for the reminders: [label => validTo]. */
function expiring_items(array $d, string $today): array
{
    $out = [];
    $applies = function (array $o) use ($today): bool {
        return ($o['validFrom'] ?? '9999') <= $today && (($o['validTo'] ?? null) === null || $o['validTo'] >= $today);
    };
    if (!empty($d['validTo'])) $out['ceniki.json (validTo)'] = $d['validTo'];
    foreach (($d['taxes'] ?? []) as $key => $list) {
        if (!is_array($list)) continue;
        foreach ($list as $c) if ($applies($c) && !empty($c['validTo'])) $out["taxes.$key"] = $c['validTo'];
    }
    foreach (($d['network'] ?? []) as $n) if ($applies($n)) $out['network ' . $n['validFrom']] = $n['validTo'];
    foreach (($d['suppliers'] ?? []) as $s) {
        foreach (($s['packages'] ?? []) as $p) if ($applies($p) && !empty($p['validTo'])) $out[$s['name'] . ' / ' . $p['name']] = $p['validTo'];
    }
    return $out;
}

function fetch_page(string $url, string $userAgent): ?string
{
    if (!function_exists('curl_init')) return null;
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_FOLLOWLOCATION => true,
        CURLOPT_MAXREDIRS => 3,
        CURLOPT_CONNECTTIMEOUT => 15,
        CURLOPT_TIMEOUT => 30,
        CURLOPT_USERAGENT => $userAgent,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
    ]);
    $body = curl_exec($ch);
    $status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);
    return ($body === false || $status !== 200) ? null : (string)$body;
}

/**
 * The part of a page that is compared: matches of the optional regular expression, otherwise all links to PDF files
 * (price lists are published as PDFs), otherwise the visible text. Scripts, styles and whitespace are ignored, so
 * that a changed banner or a session token does not count as a change.
 */
function page_fingerprint(string $html, ?string $pattern): string
{
    if ($pattern !== null && $pattern !== '' && @preg_match_all($pattern, $html, $m) > 0) {
        return hash('sha256', implode("\n", $m[0]));
    }
    if (preg_match_all('/href\s*=\s*["\']([^"\']+\.pdf[^"\']*)["\']/i', $html, $m) > 0) {
        $links = array_unique($m[1]);
        sort($links);
        return hash('sha256', implode("\n", $links));
    }
    $text = preg_replace('#<(script|style|noscript)[^>]*>.*?</\1>#is', ' ', $html);
    $text = preg_replace('/\s+/', ' ', strip_tags((string)$text));
    return hash('sha256', trim((string)$text));
}

// ---------------------------------------------------------------- 1. publish

$state = load_json($config['state_file']) ?? ['pages' => [], 'reminded' => []];
$source = load_json($config['source_file']);
$published = load_json($config['public_file']);

if ($source === null) {
    $problem = true;
    $report[] = 'NAPAKA: ceniki_vir.json manjka ali ni veljaven JSON. Objavljena datoteka ostaja nespremenjena.';
} else {
    $errors = validate($source);
    if ($errors) {
        $problem = true;
        $report[] = 'NAPAKA: ceniki_vir.json ni objavljen (objavljena datoteka ostaja nespremenjena):';
        foreach ($errors as $err) $report[] = '  - ' . $err;
    } elseif ($published === null || content_hash($published) !== content_hash($source)) {
        $source['updatedAt'] = date('c');
        $text = json_encode($source, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "\n";
        // Re-check the exact text that will be published.
        $again = json_decode($text, true);
        if (!is_array($again) || validate($again)) {
            $problem = true;
            $report[] = 'NAPAKA: zapis ni prestal ponovnega preverjanja; nič ni objavljeno.';
        } else {
            if (is_file($config['public_file'])) @copy($config['public_file'], $config['public_file'] . '.prev');
            if (write_atomically($config['public_file'], $text)) {
                $report[] = 'Objavljen nov ceniki.json (updatedAt ' . $source['updatedAt'] . ').';
                // Keep the source in step, so the next run sees no change.
                write_atomically($config['source_file'], $text);
                $published = $source;
            } else {
                $problem = true;
                $report[] = 'NAPAKA: ceniki.json ni bilo mogoče zapisati (pravice mape?).';
            }
        }
    }
}

// ---------------------------------------------------------------- 2. watch the official pages

$watch = load_json($config['watch_file']) ?? [];
foreach ($watch as $page) {
    $url = $page['url'] ?? '';
    $name = $page['name'] ?? $url;
    if (!check_url($url)) continue;
    $html = fetch_page($url, $config['user_agent']);
    if ($html === null) {
        log_line($config, "watch: $url not reachable");
        continue;
    }
    $fp = page_fingerprint($html, $page['pattern'] ?? null);
    $old = $state['pages'][$url] ?? null;
    if ($old !== null && $old !== $fp) {
        $report[] = "Sprememba na strani: $name ($url). Preveri cenik in po potrebi posodobi ceniki_vir.json.";
    }
    $state['pages'][$url] = $fp;
}

// ---------------------------------------------------------------- 3. reminders before expiry

if ($published !== null) {
    $today = date('Y-m-d');
    $limit = date('Y-m-d', strtotime('+' . (int)$config['lead_days'] . ' days'));
    foreach (expiring_items($published, $today) as $label => $validTo) {
        if ($validTo <= $limit) {
            $key = $label . '|' . $validTo;
            // One reminder per item, then one more when it has expired.
            $stage = $validTo < $today ? 'expired' : 'soon';
            if (($state['reminded'][$key] ?? '') === $stage) continue;
            $state['reminded'][$key] = $stage;
            $report[] = $stage === 'expired'
                ? "POTEKLO: $label je veljal do $validTo. Vnesi nov cenik."
                : "Kmalu poteče: $label velja do $validTo. Pripravi nov cenik.";
        }
    }
}

write_atomically($config['state_file'], json_encode($state, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));

// ---------------------------------------------------------------- report

foreach ($report as $line) log_line($config, $line);
if ($report && $config['notify_email'] !== '') {
    $subject = '=?UTF-8?B?' . base64_encode(($problem ? '[NAPAKA] ' : '') . 'Tarifni semafor - ceniki') . '?=';
    $headers = "Content-Type: text/plain; charset=UTF-8\r\n";
    if ($config['mail_from'] !== '') $headers .= 'From: ' . $config['mail_from'] . "\r\n";
    if (!@mail($config['notify_email'], $subject, implode("\n", $report), $headers)) {
        log_line($config, 'mail() failed');
    }
}
exit($problem ? 1 : 0);
