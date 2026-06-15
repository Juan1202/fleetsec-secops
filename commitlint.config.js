/**
 * commitlint config — FleetSec SecOps
 * Enforces Conventional Commits (https://www.conventionalcommits.org/en/v1.0.0/)
 * con scopes específicos del proyecto.
 *
 * Cualquier commit que no cumpla este formato hace fallar el pre-commit hook (.husky/commit-msg)
 * y también el CI (workflow security.yml > commitlint job).
 */

module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // Types permitidos — sincronizado con docs/CONVENTIONAL-COMMITS.md
    'type-enum': [
      2, // error level
      'always',
      [
        'feat',     // Nueva funcionalidad
        'fix',      // Bug fix (incluye fix(security) para remediaciones VAPT)
        'docs',     // Solo documentación
        'chore',    // Config, dependencias, mantenimiento
        'ci',       // GitHub Actions, pipeline
        'refactor', // Sin cambio funcional
        'test',     // Solo tests
        'style',    // Formato, espacios (no cambia significado)
        'perf',     // Mejora de performance
        'build',    // Build system, deps
        'revert',   // Revierte un commit anterior
      ],
    ],

    // Scopes esperados (no obligatorio pero recomendado)
    'scope-enum': [
      1, // warning level (no bloquea, solo advierte)
      'always',
      [
        'pipeline',
        'app',
        'terraform',
        'vapt',
        'ir',
        'docs',
        'infra',
        'security',
        'adr',
        'deps',
      ],
    ],

    // Reglas adicionales
    'subject-case': [2, 'never', ['pascal-case', 'upper-case']],
    'subject-empty': [2, 'never'],
    'subject-full-stop': [2, 'never', '.'],
    'header-max-length': [2, 'always', 100],
    'body-leading-blank': [2, 'always'],
    'footer-leading-blank': [2, 'always'],
  },

  /**
   * Ejemplos válidos:
   *   feat(pipeline): add ZAP DAST stage with OpenAPI coverage 80%
   *   fix(security): remediate V-01 SQL injection in /api/items endpoint
   *   chore(terraform): pin Checkov version to 3.2.x
   *   docs(adr): add ADR-003 KMS key-per-service decision
   *
   * Ejemplos INVÁLIDOS (bloquean el commit):
   *   "Update README"                          ← falta type
   *   "FIX: SQL injection"                     ← type debe ser lowercase
   *   "feat(pipeline): Add ZAP stage."         ← subject sentence-case + punto final
   *   "wip"                                    ← no es Conventional Commit
   */
};
