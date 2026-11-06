package dev.oneforallsurvival.machine;

/** Issue d'une tentative de réparation d'une machine. */
public enum RepairOutcome {
    /** La machine est remise à neuf. */
    REPAIRED,
    /** La pièce proposée n'est pas celle qui a lâché, ni celle qui lâchera. */
    WRONG_PART,
    /** La machine est déjà à neuf (et déjà traitée si on voulait la traiter). */
    NOTHING_TO_DO
}
