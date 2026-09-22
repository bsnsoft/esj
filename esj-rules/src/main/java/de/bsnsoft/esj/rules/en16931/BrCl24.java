package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.rules.CodeList;
import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code BR-CL-24}: the media type of an attached document (BT-125) is one of the six a
 * receiver of a core invoice has to be able to process.
 *
 * <p>The standard names those six in clause 6.5.11 and repeats them in the usage note of the
 * business term, which makes this an unusually narrow code list: it is a restriction of the
 * IANA media type registry to what an invoice may carry as an attachment, and the snapshot
 * the pack decides against is that restriction, with each of the six looked up in the
 * registry it belongs to.
 *
 * <p>Written in Java because a media type is a supplementary component of a binary object and
 * not a business term, so no path of the rule language reaches it, for the same reason
 * {@link SchemeIdentifier} gives about identification schemes.
 */
public final class BrCl24 implements JavaRule {

    /** Creates the rule. */
    public BrCl24() {
    }

    /** The attached document's binary object, whose media type this rule is about. */
    private static final String ATTACHMENT = "/BG-24/*/BT-125";

    @Override
    public String id() {
        return "BR-CL-24";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.FATAL;
    }

    @Override
    public String context() {
        return "/";
    }

    @Override
    public List<String> terms() {
        return List.of("BT-125");
    }

    @Override
    public List<String> roots() {
        return List.of(ATTACHMENT);
    }

    @Override
    public String source() {
        return "EN 16931-1, 6.5.11, Table 25, and 6.3, Table 2, BT-125";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        CodeList admitted = context.codeList("mime-code");
        for (Map.Entry<SemanticPath, SemanticValue> value : context.values(ATTACHMENT)) {
            String mimeCode = value.getValue().mimeCode();
            if (mimeCode == null || admitted.contains(mimeCode)) {
                continue;
            }
            return Optional.of("The attached document (BT-125) at " + value.getKey()
                    + " is of the media type " + context.escape(mimeCode) + ", which is not on"
                    + " the mime-code snapshot this pack decides against.");
        }
        return Optional.empty();
    }
}
