/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object FirTypeArgumentsNotAllowedChecker : FirQualifiedAccessChecker() {
    override fun check(functionCall: FirQualifiedAccessExpression, context: CheckerContext, reporter: DiagnosticReporter) {
        // analyze things like `T<String>`
        for (it in functionCall.typeArguments) {
            if (it is FirTypeProjectionWithVariance) {
                val that = it.typeRef.safeAs<FirResolvedTypeRef>()
                    ?: continue

                if (that.type is ConeTypeParameterType) {
                    that.reportIfTypeParameterContainsTypeArguments(reporter)
                }
            }
        }

        // analyze type parameters near
        // package names
        val explicitReceiver = functionCall.explicitReceiver

        if (explicitReceiver is FirResolvedQualifier && explicitReceiver.symbol == null) {
            explicitReceiver.reportIfReceiverContainsTypeArguments(reporter)
        }
    }

    private fun FirResolvedTypeRef.reportIfTypeParameterContainsTypeArguments(reporter: DiagnosticReporter) {
        val psi = source.psi
        val lightNode = source.lightNode

        if (psi != null && psi !is PsiErrorElement) {
            psi.firstChild.reportIfContainsTypeArguments(reporter)
        } else if (lightNode != null) {
            val tree = source.cast<FirLightSourceElement>().tree
            lightNode.reportIfContainsTypeArguments(tree, reporter)
        }
    }

    private fun FirResolvedQualifier.reportIfReceiverContainsTypeArguments(reporter: DiagnosticReporter) {
        val psi = source.psi
        val lightNode = source.lightNode

        if (psi != null) {
            psi.reportIfContainsTypeArguments(reporter)
        } else if (lightNode != null) {
            val tree = source.cast<FirLightSourceElement>().tree
            lightNode.reportIfContainsTypeArguments(tree, reporter)
        }
    }

    private fun PsiElement.reportIfContainsTypeArguments(reporter: DiagnosticReporter) {
        if (this !is PsiErrorElement) {
            val possibleTypeArgumentsList = this.node.lastChildNode.elementType

            if (possibleTypeArgumentsList == KtStubElementTypes.TYPE_ARGUMENT_LIST) {
                reporter.report(this.node.lastChildNode.psi.toFirPsiSourceElement())
            }
        }
    }

    private fun LighterASTNode.reportIfContainsTypeArguments(
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        reporter: DiagnosticReporter
    ) {
        val possibleTypeArgumentsList = this.getLastChild(tree)

        if (
            possibleTypeArgumentsList != null &&
            possibleTypeArgumentsList.tokenType == KtStubElementTypes.TYPE_ARGUMENT_LIST
        ) {
            reporter.report(toFirLightSourceElement(possibleTypeArgumentsList.startOffset, possibleTypeArgumentsList.endOffset, tree))
        }
    }

    private fun LighterASTNode.getFirstChild(
        tree: FlyweightCapableTreeStructure<LighterASTNode>
    ): LighterASTNode? = getChildren(tree).firstOrNull()

    private fun LighterASTNode.getLastChild(
        tree: FlyweightCapableTreeStructure<LighterASTNode>
    ): LighterASTNode? = getChildren(tree).lastOrNull()

    private fun LighterASTNode.getChildren(tree: FlyweightCapableTreeStructure<LighterASTNode>): Array<out LighterASTNode> {
        val childrenRef = Ref<Array<LighterASTNode>>()
        tree.getChildren(this, childrenRef)
        return childrenRef.get()
    }

    private fun DiagnosticReporter.report(source: FirSourceElement?) {
        source?.let {
            report(FirErrors.TYPE_ARGUMENTS_NOT_ALLOWED.on(it))
        }
    }
}