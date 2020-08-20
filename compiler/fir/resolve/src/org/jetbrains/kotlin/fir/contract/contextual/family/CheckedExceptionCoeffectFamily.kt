/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.contract.contextual.family

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.contract.contextual.*
import org.jetbrains.kotlin.fir.contract.contextual.diagnostics.CoeffectContextVerificationError
import org.jetbrains.kotlin.fir.contract.contextual.diagnostics.MissingCoeffectContextError
import org.jetbrains.kotlin.fir.contract.contextual.diagnostics.UnexpectedCoeffectContextError
import org.jetbrains.kotlin.fir.contracts.description.ConeCalledInTryCatchEffectDeclaration
import org.jetbrains.kotlin.fir.contracts.description.ConeThrowsEffectDeclaration
import org.jetbrains.kotlin.fir.resolve.transformers.contracts.toCoeffect
import org.jetbrains.kotlin.fir.resolve.transformers.contracts.toLambdaCoeffect
import org.jetbrains.kotlin.fir.typeContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.types.AbstractTypeChecker

object CheckedExceptionCoeffectFamily : CoeffectFamily {

    override val emptyContext = CheckedExceptionCoeffectContext(setOf())
    override val combiner = CheckedExceptionCoeffectContextCombiner
}

data class CheckedExceptionCoeffectContext(val catchesExceptions: Set<Pair<ConeKotlinType, FirElement>>) : CoeffectContext

class CatchesExceptionCoeffectContextProvider(val exceptionType: ConeKotlinType, val marker: FirElement) : CoeffectContextProvider {
    override val family = CheckedExceptionCoeffectFamily

    override fun provideContext(context: CoeffectContext): CoeffectContext {
        if (context !is CheckedExceptionCoeffectContext) throw AssertionError()
        return CheckedExceptionCoeffectContext(context.catchesExceptions + (exceptionType to marker))
    }
}

class CheckedExceptionCoeffectContextCleaner(val exceptionType: ConeKotlinType, val marker: FirElement) : CoeffectContextCleaner {
    override val family = CheckedExceptionCoeffectFamily

    override fun cleanupContext(context: CoeffectContext): CoeffectContext {
        if (context !is CheckedExceptionCoeffectContext) throw AssertionError()
        return CheckedExceptionCoeffectContext(context.catchesExceptions - (exceptionType to marker))
    }
}

class CheckedExceptionContextError(val exceptionType: ConeKotlinType) : CoeffectContextVerificationError

class CheckedExceptionCoeffectContextVerifier(val exceptionType: ConeKotlinType) : CoeffectContextVerifier {
    override val family = CheckedExceptionCoeffectFamily

    override fun verifyContext(context: CoeffectContext, session: FirSession): List<CoeffectContextVerificationError> {
        if (context !is CheckedExceptionCoeffectContext) throw AssertionError()
        val checked = context.catchesExceptions.any { AbstractTypeChecker.isSubtypeOf(session.typeContext, exceptionType, it.first) }
        return if (!checked) {
            listOf(CheckedExceptionContextError(exceptionType))
        } else emptyList()
    }
}

object CheckedExceptionCoeffectContextCombiner : CoeffectContextCombiner {
    override fun merge(left: CoeffectContext, right: CoeffectContext): CoeffectContext {
        if (left !is CheckedExceptionCoeffectContext || right !is CheckedExceptionCoeffectContext) throw AssertionError()
        val exceptions = left.catchesExceptions.filterTo(mutableSetOf()) { it in right.catchesExceptions }
        return CheckedExceptionCoeffectContext(exceptions)
    }
}

fun asCoeffect(effect: ConeThrowsEffectDeclaration) = effect.toCoeffect {
    onOwnerCall {
        CoeffectContextActions(
            verifier = CheckedExceptionCoeffectContextVerifier(effect.exceptionType)
        )
    }

    onOwnerEnter {
        CoeffectContextActions(
            provider = CatchesExceptionCoeffectContextProvider(effect.exceptionType, it)
        )
    }
}

fun asCoeffect(effect: ConeCalledInTryCatchEffectDeclaration) = effect.toLambdaCoeffect(effect.lambda) {
    onOwnerCall {
        CoeffectContextActions(
            verifier = CheckedExceptionCoeffectContextVerifier(effect.exceptionType)
        )
    }

    onOwnerEnter {
        CoeffectContextActions(
            provider = CatchesExceptionCoeffectContextProvider(effect.exceptionType, it)
        )
    }
}