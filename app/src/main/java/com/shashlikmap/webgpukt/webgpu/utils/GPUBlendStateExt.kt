package com.shashlikmap.webgpukt.webgpu.utils

import androidx.webgpu.BlendFactor
import androidx.webgpu.BlendOperation
import androidx.webgpu.GPUBlendComponent
import androidx.webgpu.GPUBlendState

/**
 * Blend mode that does standard alpha blending with non-premultiplied alpha.
 */
val GPUBlendState.alphaBlending: GPUBlendState
    get() = GPUBlendState(
        alpha = GPUBlendComponent(
            srcFactor = BlendFactor.One,
            dstFactor = BlendFactor.OneMinusSrcAlpha,
            operation = BlendOperation.Add
        ),
        color = GPUBlendComponent(
            srcFactor = BlendFactor.SrcAlpha,
            dstFactor = BlendFactor.OneMinusSrcAlpha,
            operation = BlendOperation.Add
        )
    )