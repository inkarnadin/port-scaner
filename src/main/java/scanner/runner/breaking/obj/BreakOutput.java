package scanner.runner.breaking.obj;

import lombok.Builder;
import lombok.Getter;
import scanner.runner.Target;

import java.util.Set;

/**
 * Output break object wrapper.
 *
 * @author inkarnadin
 * on 01-10-2022
 */
@Getter
@Builder
public class BreakOutput {

    private final Set<Target> targets;

}